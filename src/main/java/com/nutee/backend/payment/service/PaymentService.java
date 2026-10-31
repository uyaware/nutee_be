package com.nutee.backend.payment.service;

import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.payment.adapter.PaymentProperties;
import com.nutee.backend.payment.port.PaymentGateway;
import com.nutee.backend.payment.repository.PaymentAttemptRepository;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final PaymentAttemptRepository attempts;
    private final com.nutee.backend.order.repository.OrderRepository orders;
    public PaymentTransactions.View create(UUID owner, int id) {
        if (!gateway.enabled()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Thanh toán payOS chưa được cấu hình.");
        var prepared = transactions.prepare(owner, id);
        if (prepared.code() == null) return prepared.view();
        String url = properties.frontendUrl().replaceAll("/+$", "") + "/orders/" + id + "/payment";
        try {
            var link = gateway.create(new PaymentGateway.Request(prepared.code(), prepared.view().amount(), prepared.view().expiresAt(), url, url));
            return transactions.linked(id, prepared.lease(), link);
        } catch (RuntimeException exception) { transactions.failed(id, prepared.lease()); throw exception; }
    }
    public PaymentTransactions.View get(UUID owner, int id) { return transactions.get(owner, id); }
    public PaymentTransactions.View refresh(UUID owner, int id) {
        var snapshot = transactions.snapshot(owner, id);
        var state = snapshot.expiresAt().isAfter(Instant.now()) ? gateway.retrieve(snapshot.code()) : gateway.cancel(snapshot.code());
        transactions.reconciled(id, state); return transactions.get(owner, id);
    }
    public void cancel(UUID owner, int id) { if (transactions.cancelUnstartedQr(owner, id)) return; var snapshot = transactions.snapshot(owner, id); transactions.reconciled(id, gateway.cancel(snapshot.code())); }
    public void webhook(String body) {
        var verified = gateway.verify(body);
        try { transactions.webhook(verified); }
        catch (DataIntegrityViolationException exception) { if (!transactions.duplicate(verified)) throw exception; }
    }
    /** Đối soát link đang chờ và hết hạn; không tự hoàn kho khi provider không xác nhận được. */
    @Scheduled(initialDelayString = "${app.payment.reconcile-delay:60000}", fixedDelayString = "${app.payment.reconcile-delay:60000}")
    public void reconcile() {
        orders.expiredUnstarted(Instant.now(), PageRequest.of(0, 50)).forEach(transactions::expireUnstarted);
        if (!gateway.enabled()) return;
        var pending = attempts.findByStatusInOrderByUpdatedAtAsc(List.of("pending", "error", "creating", "review"), PageRequest.of(0, 50));
        for (var attempt : pending) {
            if (attempt.getLeaseUntil() != null && attempt.getLeaseUntil().isAfter(Instant.now())) continue;
            try {
                var state = attempt.getExpiresAt().isAfter(Instant.now()) ? gateway.retrieve(attempt.getGatewayOrderCode()) : gateway.cancel(attempt.getGatewayOrderCode());
                transactions.reconciled(attempt.getOrderId(), state);
            } catch (ApiException exception) { transactions.reconciliationFailed(attempt.getOrderId()); log.warn("Payment reconciliation postponed for order {}", attempt.getOrderId()); }
        }
    }
}
