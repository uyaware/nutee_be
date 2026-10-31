package com.nutee.backend.order.entity;

import com.nutee.backend.account.entity.UserAddress;
import com.nutee.backend.user.entity.User;
import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;
import java.security.SecureRandom;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.Getter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import com.nutee.backend.payment.port.PaymentGateway;

/** Đơn giữ snapshot giá, khóa địa chỉ và idempotency; liên hệ lấy từ user. */
@Entity @Table(name = "orders") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(name = "order_code", length = 10) private String orderCode;
    @Column(name = "user_id") private UUID userId;
    @Column(name = "voucher_id") private UUID voucherId;
    @Column(name = "user_address_id") private Integer addressId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false) private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_address_id", insertable = false, updatable = false) private UserAddress address;
    private String status;
    @Column(name = "idempotency_key") private UUID idempotencyKey;
    @Column(name = "request_hash", length = 64) private String requestHash;
    @Column(name = "sub_total", precision = 18, scale = 2) private BigDecimal subtotal;
    @Column(name = "discount_amount", precision = 18, scale = 2) private BigDecimal discount;
    @Column(name = "total_amount", precision = 18, scale = 2) private BigDecimal total;
    @Column(name = "shipping_fee") private Integer shipping;
    @Column(name = "promotion_discount") private long promotionDiscount;
    @Column(name = "voucher_discount") private long voucherDiscount;
    @Column(name = "payment_method") private String paymentMethod;
    @Column(name = "payment_status") private String paymentStatus;
    @Column(name = "payment_amount", precision = 18, scale = 2) private BigDecimal paymentAmount;
    @Column(name = "payment_expired_at") private Instant paymentExpiresAt;
    @Generated(event = EventType.INSERT)
    @Column(name = "gateway_order_code", insertable = false, updatable = false) private Long gatewayOrderCode;
    @Column(name = "checkout_url", columnDefinition = "text") private String checkoutUrl;
    @Column(name = "qr_code", columnDefinition = "text") private String qrCode;
    @Column(name = "bank_bin", length = 20) private String bankBin;
    @Column(name = "account_number", length = 100) private String accountNumber;
    @Column(name = "account_name") private String accountName;
    @Column(name = "transfer_content") private String transferContent;
    @Column(name = "payment_attempt_count") private int paymentAttemptCount;
    @Column(columnDefinition = "text") private String note;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at") private Instant updatedAt;

    public static Order create(User owner, UUID key, String hash, UserAddress address,
            String note, String method, long subtotal, long promotion, long voucher, int shipping, UUID voucherId, Instant expiresAt) {
        Order order = new Order();
        String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"; SecureRandom random = new SecureRandom(); StringBuilder code = new StringBuilder();
        for (int i = 0; i < 10; i++) code.append(alphabet.charAt(random.nextInt(alphabet.length())));
        order.orderCode = code.toString(); order.userId = owner.getId(); order.user = owner; order.idempotencyKey = key; order.requestHash = hash;
        order.addressId = address.getId(); order.address = address; order.note = note; order.paymentMethod = method;
        order.subtotal = BigDecimal.valueOf(subtotal); order.promotionDiscount = promotion; order.voucherDiscount = voucher;
        order.discount = BigDecimal.valueOf(promotion + voucher); order.shipping = shipping;
        order.total = BigDecimal.valueOf(subtotal - promotion - voucher + shipping); order.voucherId = voucherId;
        order.status = "pending"; order.paymentStatus = "qr".equals(method) ? "pending" : "unpaid"; order.paymentAmount = BigDecimal.ZERO;
        order.paymentExpiresAt = "qr".equals(method) ? expiresAt : null;
        if (order.total.signum() == 0) { order.status = "confirmed"; order.paymentStatus = "paid"; }
        return order;
    }

    /** Xác nhận chỉ từ provider đã xác minh; tiền đến sau khi đã hoàn kho cần đối soát thủ công. */
    public void paid() {
        if ("paid".equals(paymentStatus)) return;
        paymentAmount = total;
        if ("cancelled".equals(status)) { paymentStatus = "review"; return; }
        paymentStatus = "paid"; status = "confirmed";
    }
    public void review() { if (!"paid".equals(paymentStatus)) paymentStatus = "review"; }
    public void cancelled() { status = "cancelled"; paymentStatus = "qr".equals(paymentMethod) ? "cancelled" : "unpaid"; }

    /** Đếm lần tạo để phản hồi cũ không ghi đè lần thử mới; service giữ khóa dòng đơn. */
    public int startPayment() {
        paymentStatus = "creating";
        transferContent = orderCode;
        updatedAt = Instant.now();
        return ++paymentAttemptCount;
    }

    public void paymentLinked(PaymentGateway.Link link) {
        checkoutUrl = link.checkoutUrl();
        if (link.qrCode() != null) {
            qrCode = link.qrCode();
            bankBin = link.bankBin();
            accountNumber = link.accountNumber();
            accountName = link.accountName();
            transferContent = link.transferContent();
        }
        if (!java.util.Set.of("paid", "review", "cancelled", "expired").contains(paymentStatus)) paymentStatus = "pending";
    }

    public void paymentFailed() {
        if (checkoutUrl == null && !java.util.Set.of("paid", "review", "cancelled", "expired").contains(paymentStatus)) paymentStatus = "error";
    }

    public void paymentExpired() { paymentStatus = "expired"; }
    public void paymentPending() { paymentStatus = "pending"; }
    public void touchPayment() { updatedAt = Instant.now(); }
}
