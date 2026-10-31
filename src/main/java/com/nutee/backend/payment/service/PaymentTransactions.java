package com.nutee.backend.payment.service;

import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.order.entity.Order;
import com.nutee.backend.order.item.repository.OrderItemRepository;
import com.nutee.backend.order.repository.OrderRepository;
import com.nutee.backend.payment.entity.*;
import com.nutee.backend.payment.port.PaymentGateway;
import com.nutee.backend.payment.repository.*;
import com.nutee.backend.product.variant.repository.ProductVariantRepository;
import com.nutee.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Chỉ chứa transaction ngắn. Gọi provider luôn diễn ra ngoài lớp này. */
@Service @RequiredArgsConstructor @Transactional
public class PaymentTransactions {
    private final OrderRepository orders;
    private final PaymentAttemptRepository attempts;
    private final PaymentEventRepository events;
    private final OrderItemRepository items;
    private final ProductVariantRepository variants;
    private final UserRepository users;
    public record View(int orderId, String status, long amount, String checkoutUrl, Instant expiresAt) {}
    public record Prepared(View view, Long code, UUID lease) {}
    public record Snapshot(int orderId, long code, long amount, String externalId, Instant expiresAt) {}

    public Prepared prepare(UUID owner, int id) {
        Order order = ownedLocked(owner, id);
        if (!"qr".equals(order.getPaymentMethod()) || order.getIdempotencyKey() == null || "cancelled".equals(order.getStatus())) throw conflict("Đơn này không thể tạo link thanh toán.");
        if ("paid".equals(order.getPaymentStatus())) return new Prepared(new View(id, "paid", order.getTotal().longValueExact(), null, order.getPaymentExpiresAt()), null, null);
        if (!order.getPaymentExpiresAt().isAfter(Instant.now())) throw conflict("Đơn đã hết thời gian thanh toán. Hãy kiểm tra trạng thái hoặc hủy đơn.");
        var attempt = attempts.findLockedByOrderId(id).orElseGet(() -> attempts.saveAndFlush(new PaymentAttempt(id, order.getTotal().longValueExact(), order.getPaymentExpiresAt())));
        if (attempt.getExternalId() != null || "review".equals(attempt.getStatus()) || (attempt.getLeaseUntil() != null && attempt.getLeaseUntil().isAfter(Instant.now()))) return new Prepared(view(attempt), null, null);
        UUID lease = attempt.claim(); return new Prepared(view(attempt), attempt.getGatewayOrderCode(), lease);
    }
    public View linked(int orderId, UUID lease, PaymentGateway.Link link) {
        orders.findLockedById(orderId).orElseThrow();
        var attempt = attempts.findLockedByOrderId(orderId).orElseThrow();
        if (!Objects.equals(lease, attempt.getLeaseToken())) return view(attempt);
        if (attempt.getGatewayOrderCode() != link.orderCode() || attempt.getAmount() != link.amount() || !"VND".equals(link.currency())) throw conflict("Dữ liệu link không khớp với đơn.");
        if (attempt.getExternalId() != null && !attempt.getExternalId().equals(link.externalId())) throw conflict("Link thanh toán không khớp.");
        attempt.link(link); return view(attempt);
    }
    public void failed(int orderId, UUID lease) { orders.findLockedById(orderId).orElseThrow(); attempts.findLockedByOrderId(orderId).filter(value -> Objects.equals(lease, value.getLeaseToken())).ifPresent(PaymentAttempt::error); }

    @Transactional(readOnly = true)
    public View get(UUID owner, int id) {
        active(owner); var order = orders.findByIdAndUserId(id, owner).orElseThrow(() -> missing());
        if (order.getIdempotencyKey() == null) throw conflict("Đơn cũ chưa hỗ trợ luồng thanh toán này.");
        return attempts.findByOrderId(id).map(this::view).orElse(new View(id, order.getPaymentStatus(), order.getTotal().longValueExact(), null, order.getPaymentExpiresAt()));
    }
    @Transactional(readOnly = true)
    public Snapshot snapshot(UUID owner, int id) {
        if (owner != null) { active(owner); if (orders.findByIdAndUserId(id, owner).isEmpty()) throw missing(); }
        var attempt = attempts.findByOrderId(id).orElseThrow(() -> conflict("Đơn chưa có phiên thanh toán."));
        return new Snapshot(id, attempt.getGatewayOrderCode(), attempt.getAmount(), attempt.getExternalId(), attempt.getExpiresAt());
    }

    /** Callback đã xác minh được đối chiếu reference, mã đơn, link, tiền và VND trước khi cập nhật. */
    public void webhook(PaymentGateway.Event event) {
        var ownerOrder = attempts.orderIdForCode(event.orderCode());
        Order order = ownerOrder.map(id -> orders.findLockedById(id).orElseThrow()).orElse(null);
        var existing = events.findById("payos:" + event.reference());
        if (existing.isPresent()) { if (!existing.get().matches(event)) throw conflict("Reference webhook đã được dùng với dữ liệu khác."); return; }
        String decision = "unknown_order";
        if (order != null) {
            var attempt = attempts.findLockedByOrderId(order.getId()).orElseThrow();
            if (!"00".equals(event.code())) decision = "ignored_status";
            else if (!"VND".equals(event.currency()) || (attempt.getExternalId() != null && !Objects.equals(attempt.getExternalId(), event.externalId())) || attempt.getAmount() != event.amount()) {
                decision = "mismatch"; if (!"paid".equals(attempt.getStatus())) { attempt.status("review"); order.review(); }
            } else if ("paid".equals(attempt.getStatus())) decision = "additional_payment";
            else { if (attempt.getExternalId() == null) attempt.bind(event.externalId()); order.paid(); attempt.status(order.getPaymentStatus()); decision = "paid".equals(order.getPaymentStatus()) ? "paid" : "late_payment"; }
        }
        events.saveAndFlush(new PaymentEvent(event, decision));
    }
    @Transactional(readOnly = true)
    public boolean duplicate(PaymentGateway.Event event) { return events.findById("payos:" + event.reference()).filter(value -> value.matches(event)).isPresent(); }

    /** Kết quả API payOS đã xác minh hỗ trợ webhook mất/chậm và hủy/hết hạn, không tin browser redirect. */
    public void reconciled(int id, PaymentGateway.State state) {
        var order = orders.findLockedById(id).orElseThrow(); var attempt = attempts.findLockedByOrderId(id).orElseThrow();
        if (state.orderCode() != attempt.getGatewayOrderCode() || state.amount() != attempt.getAmount()
                || (attempt.getExternalId() != null && !attempt.getExternalId().equals(state.externalId()))) throw conflict("Kết quả đối soát không khớp.");
        if ("paid".equals(attempt.getStatus())) return;
        if (attempt.getExternalId() == null) attempt.bind(state.externalId());
        if ("PAID".equals(state.status()) && state.amountPaid() == attempt.getAmount()) { order.paid(); attempt.status(order.getPaymentStatus()); }
        else if (state.amountPaid() > 0 || "review".equals(attempt.getStatus())) { order.review(); attempt.status("review"); }
        else if (Set.of("CANCELLED", "EXPIRED").contains(state.status())) { release(order); attempt.status(state.status().toLowerCase(Locale.ROOT)); }
        else if (!Set.of("review", "cancelled", "expired").contains(attempt.getStatus())) attempt.status("pending");
        attempt.touch();
    }
    public void reconciliationFailed(int id) { orders.findLockedById(id).ifPresent(order -> attempts.findLockedByOrderId(id).ifPresent(PaymentAttempt::touch)); }
    public void expireUnstarted(int id) {
        var order = orders.findLockedById(id).orElseThrow();
        if ("pending".equals(order.getStatus()) && "pending".equals(order.getPaymentStatus()) && !order.getPaymentExpiresAt().isAfter(Instant.now()) && attempts.findLockedByOrderId(id).isEmpty()) release(order);
    }
    public boolean cancelUnstartedQr(UUID owner, int id) {
        var order = ownedLocked(owner, id);
        if (order.getIdempotencyKey() == null || !"qr".equals(order.getPaymentMethod()) || "paid".equals(order.getPaymentStatus())) throw conflict("Đơn không thể hủy.");
        if (attempts.findLockedByOrderId(id).isPresent()) return false;
        release(order); return true;
    }
    public void cancelCod(UUID owner, int id) {
        var order = ownedLocked(owner, id);
        if (!"cod".equals(order.getPaymentMethod()) || order.getIdempotencyKey() == null || !Set.of("pending", "cancelled").contains(order.getStatus()) || "paid".equals(order.getPaymentStatus())) throw conflict("Đơn không thể hủy.");
        release(order);
    }
    private void release(Order order) {
        if ("cancelled".equals(order.getStatus())) return;
        if ("paid".equals(order.getPaymentStatus())) throw conflict("Đơn đã được thanh toán.");
        var rows = items.reservations(order.getId());
        var locked = variants.findLockedByIdIn(rows.stream().map(OrderItemRepository.Reservation::getVariantId).toList(), Sort.by("id"));
        var quantities = new HashMap<Integer, Integer>(); rows.forEach(item -> quantities.merge(item.getVariantId(), item.getQuantity(), Integer::sum));
        locked.forEach(variant -> variant.release(quantities.get(variant.getId()))); order.cancelled();
    }
    private Order ownedLocked(UUID owner, int id) { active(owner); var order = orders.findLockedById(id).orElseThrow(() -> missing()); if (!order.getUserId().equals(owner)) throw missing(); return order; }
    private void active(UUID owner) { if (users.findById(owner).filter(user -> user.isActive()).isEmpty()) throw new BadCredentialsException("Invalid account."); }
    private View view(PaymentAttempt value) { return new View(value.getOrderId(), value.getStatus(), value.getAmount(), value.getCheckoutUrl(), value.getExpiresAt()); }
    private ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, message); }
    private ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng."); }
}
