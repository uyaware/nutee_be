package com.nutee.backend.payment.service;

import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.payment.adapter.PaymentProperties;
import com.nutee.backend.payment.port.PaymentGateway;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

/** Orchestrator không mở transaction trong lúc SDK gọi mạng; phối hợp port và DB riêng. */
@Service @RequiredArgsConstructor @Slf4j
public class PaymentService {
    private final PaymentTransactions transactions;
    private final PaymentGateway gateway;
    private final PaymentProperties properties;
    private final ObjectMapper json;
    private final com.nutee.backend.order.repository.OrderRepository orders;
    public PaymentTransactions.View create(UUID owner, int id) {
        if (!gateway.enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Thanh toán payOS chưa được cấu hình.");
        var prepared = transactions.prepare(owner, id);
        if (prepared.code() == null) return prepared.view();
        String url = properties.frontendUrl().replaceAll("/+$", "") + "/orders/" + id + "/payment";
        try {
            var link = gateway.create(new PaymentGateway.Request(prepared.code(), prepared.view().amount(), prepared.view().expiresAt(), url, url, prepared.view().transferContent()));
            return transactions.linked(id, prepared.attempt(), link);
        } catch (RuntimeException exception) { transactions.failed(id, prepared.attempt()); throw exception; }
    }
    public PaymentTransactions.View get(UUID owner, int id) { return transactions.get(owner, id); }
    public PaymentTransactions.View refresh(UUID owner, int id) {
        var snapshot = transactions.snapshot(owner, id);
        var state = snapshot.expiresAt().isAfter(Instant.now()) ? gateway.retrieve(snapshot.code()) : gateway.cancel(snapshot.code());
        transactions.reconciled(id, state); return transactions.get(owner, id);
    }
    public void cancel(UUID owner, int id) { if (transactions.cancelUnstartedQr(owner, id)) return; var snapshot = transactions.snapshot(owner, id); transactions.reconciled(id, gateway.cancel(snapshot.code())); }
    public void webhook(String body) { webhook(body, Map.of()); }

    /** Lưu payload nhận được sau khi xác minh chữ ký; không đưa cookie/token vào log webhook. */
    public void webhook(String body, Map<String, List<String>> headers) {
        var verified = gateway.verify(body);
        try {
            Map<String, Object> payload = json.readValue(body, new TypeReference<>() {});
            var safeHeaders = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
            headers.forEach((name, values) -> {
                if (!name.toLowerCase(Locale.ROOT).matches(".*(authorization|cookie|token|api.key|secret).*")) safeHeaders.put(name, values);
            });
            String decision = transactions.webhook(verified, payload, json.writeValueAsString(safeHeaders));
            if ("reference_conflict".equals(decision)) throw new ApiException(HttpStatus.CONFLICT, "Reference webhook đã được dùng với dữ liệu khác.");
        } catch (JsonProcessingException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "Webhook payOS không hợp lệ."); }
    }
    /** Đối soát link đang chờ và hết hạn; không tự hoàn kho khi provider không xác nhận được. */
    @Scheduled(initialDelayString = "${app.payment.reconcile-delay:60000}", fixedDelayString = "${app.payment.reconcile-delay:60000}")
    public void reconcile() {
        orders.expiredUnstarted(Instant.now(), PageRequest.of(0, 50)).forEach(transactions::expireUnstarted);
        if (!gateway.enabled()) return;
        var pending = orders.findByPaymentMethodAndPaymentAttemptCountGreaterThanAndPaymentStatusInOrderByUpdatedAtAsc(
                "qr", 0, List.of("pending", "error", "creating", "review"), PageRequest.of(0, 50));
        for (var order : pending) {
            if ("creating".equals(order.getPaymentStatus()) && order.getUpdatedAt().isAfter(Instant.now().minusSeconds(30))) continue;
            try {
                var state = order.getPaymentExpiresAt().isAfter(Instant.now()) ? gateway.retrieve(order.getGatewayOrderCode()) : gateway.cancel(order.getGatewayOrderCode());
                transactions.reconciled(order.getId(), state);
            } catch (ApiException exception) { transactions.reconciliationFailed(order.getId()); log.warn("Payment reconciliation postponed for order {}", order.getId()); }
        }
    }
}
