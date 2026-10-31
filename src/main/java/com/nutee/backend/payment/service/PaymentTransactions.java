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
    private final TransactionLogRepository logs;
    private final OrderItemRepository items;
    private final ProductVariantRepository variants;
    private final UserRepository users;
    public record View(int orderId, String status, long amount, String checkoutUrl, String qrCode,
            String bankBin, String accountNumber, String accountName, String transferContent, Instant expiresAt) {
        @Override public String toString() {
            return "PaymentView[orderId=" + orderId + ", status=" + status + ", amount=" + amount + ", qrCode=REDACTED]";
        }
    }
    public record Prepared(View view, Long code, int attempt) {}
    public record Snapshot(int orderId, long code, long amount, Instant expiresAt) {}

    public Prepared prepare(UUID owner, int id) {
        Order order = ownedLocked(owner, id);
        if (!"qr".equals(order.getPaymentMethod()) || order.getIdempotencyKey() == null || "cancelled".equals(order.getStatus())) throw conflict("Đơn này không thể tạo link thanh toán.");
        if (Set.of("paid", "review", "expired").contains(order.getPaymentStatus())) return new Prepared(view(order), null, 0);
        if (!order.getPaymentExpiresAt().isAfter(Instant.now())) throw conflict("Đơn đã hết thời gian thanh toán. Hãy kiểm tra trạng thái hoặc hủy đơn.");
        if (order.getCheckoutUrl() != null || ("creating".equals(order.getPaymentStatus()) && order.getUpdatedAt().isAfter(Instant.now().minusSeconds(30))))
            return new Prepared(view(order), null, 0);
        int attempt = order.startPayment();
        return new Prepared(view(order), order.getGatewayOrderCode(), attempt);
    }
    public View linked(int orderId, int attempt, PaymentGateway.Link link) {
        var order = orders.findLockedById(orderId).orElseThrow();
        if (attempt != order.getPaymentAttemptCount()) return view(order);
        if (order.getGatewayOrderCode() != link.orderCode() || order.getTotal().longValueExact() != link.amount() || !"VND".equals(link.currency())
                || (link.qrCode() != null && !order.getOrderCode().equals(link.transferContent()))) throw conflict("Dữ liệu link không khớp với đơn.");
        order.paymentLinked(link); return view(order);
    }
    public void failed(int orderId, int attempt) { orders.findLockedById(orderId).filter(order -> order.getPaymentAttemptCount() == attempt).ifPresent(Order::paymentFailed); }

    @Transactional(readOnly = true)
    public View get(UUID owner, int id) {
        active(owner); var order = orders.findByIdAndUserId(id, owner).orElseThrow(() -> missing());
        if (order.getIdempotencyKey() == null) throw conflict("Đơn cũ chưa hỗ trợ luồng thanh toán này.");
        return view(order);
    }
    @Transactional(readOnly = true)
    public Snapshot snapshot(UUID owner, int id) {
        if (owner != null) active(owner);
        var order = orders.findById(id).orElseThrow(() -> missing());
        if (owner != null && !owner.equals(order.getUserId())) throw missing();
        if (!"qr".equals(order.getPaymentMethod()) || order.getPaymentAttemptCount() == 0) throw conflict("Đơn chưa có phiên thanh toán.");
        return new Snapshot(id, order.getGatewayOrderCode(), order.getTotal().longValueExact(), order.getPaymentExpiresAt());
    }

    /** Khóa đơn để ghi nhận tiền một lần; mỗi lần nhận webhook vẫn có log riêng. */
    public String webhook(PaymentGateway.Event event, Map<String, Object> body, String headers) {
        Order order = orders.orderIdForPaymentCode(event.orderCode())
                .map(id -> orders.findLockedById(id).orElseThrow()).orElse(null);
        var existing = logs.findFirstByProviderAndReferenceOrderByIdAsc("payos", "payos:" + event.reference());
        String decision = "unknown_order";
        if (existing.isPresent()) decision = existing.get().matches(event) ? "duplicate" : "reference_conflict";
        else if (order != null && "qr".equals(order.getPaymentMethod()) && order.getPaymentAttemptCount() > 0) {
            if (!"00".equals(event.code())) decision = "ignored_status";
            else if (!"VND".equals(event.currency()) || order.getTotal().longValueExact() != event.amount() || !matchesLink(order, event.externalId())) {
                decision = "mismatch"; order.review();
            } else if ("paid".equals(order.getPaymentStatus())) decision = "additional_payment";
            else { order.paid(); decision = "paid".equals(order.getPaymentStatus()) ? "paid" : "late_payment"; }
        }
        logs.save(new TransactionLog(order == null ? null : order.getId(), event, body, headers, decision));
        return decision;
    }

    /** Kết quả API payOS đã xác minh hỗ trợ webhook mất/chậm và hủy/hết hạn, không tin browser redirect. */
    public void reconciled(int id, PaymentGateway.State state) {
        var order = orders.findLockedById(id).orElseThrow();
        if (state.orderCode() != order.getGatewayOrderCode() || state.amount() != order.getTotal().longValueExact() || !matchesLink(order, state.externalId())) throw conflict("Kết quả đối soát không khớp.");
        if ("paid".equals(order.getPaymentStatus())) return;
        if ("PAID".equals(state.status()) && state.amountPaid() == order.getTotal().longValueExact()) order.paid();
        else if (state.amountPaid() > 0 || "review".equals(order.getPaymentStatus())) order.review();
        else if (Set.of("CANCELLED", "EXPIRED").contains(state.status())) { release(order); if ("EXPIRED".equals(state.status())) order.paymentExpired(); }
        else if (!Set.of("review", "cancelled", "expired").contains(order.getPaymentStatus())) order.paymentPending();
        order.touchPayment();
    }
    public void reconciliationFailed(int id) { orders.findLockedById(id).ifPresent(Order::touchPayment); }
    public void expireUnstarted(int id) {
        var order = orders.findLockedById(id).orElseThrow();
        if ("pending".equals(order.getStatus()) && "pending".equals(order.getPaymentStatus()) && !order.getPaymentExpiresAt().isAfter(Instant.now()) && order.getPaymentAttemptCount() == 0) release(order);
    }
    public boolean cancelUnstartedQr(UUID owner, int id) {
        var order = ownedLocked(owner, id);
        if (order.getIdempotencyKey() == null || !"qr".equals(order.getPaymentMethod()) || "paid".equals(order.getPaymentStatus())) throw conflict("Đơn không thể hủy.");
        if (order.getPaymentAttemptCount() > 0) return false;
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
    private boolean matchesLink(Order order, String linkId) {
        return order.getCheckoutUrl() == null || java.net.URI.create(order.getCheckoutUrl()).getPath().endsWith("/" + linkId);
    }
    private View view(Order order) {
        return new View(order.getId(), order.getPaymentStatus(), order.getTotal().longValueExact(), order.getCheckoutUrl(),
                order.getQrCode(), order.getBankBin(), order.getAccountNumber(), order.getAccountName(), order.getTransferContent(), order.getPaymentExpiresAt());
    }
    private ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, message); }
    private ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng."); }
}
