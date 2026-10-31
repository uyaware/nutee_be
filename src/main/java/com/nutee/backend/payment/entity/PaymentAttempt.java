package com.nutee.backend.payment.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import com.nutee.backend.payment.port.PaymentGateway;

/** Mỗi đơn dùng một mã payOS ổn định; lease bảo vệ các lần tạo link đồng thời/mất response. */
@Entity @Table(name = "transaction_logs") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(name = "order_id", nullable = false, unique = true) private Integer orderId;
    @Generated(event = EventType.INSERT) @Column(name = "gateway_order_code", insertable = false, updatable = false) private Long gatewayOrderCode;
    private String provider = "payos";
    private long amount;
    private String currency = "VND";
    private String status = "new";
    @Column(name = "external_id") private String externalId;
    @Column(name = "checkout_url", columnDefinition = "text") private String checkoutUrl;
    @Column(name = "lease_token") private UUID leaseToken;
    @Column(name = "lease_until") private Instant leaseUntil;
    @Column(name = "expires_at") private Instant expiresAt;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at") private Instant updatedAt;
    public PaymentAttempt(int orderId, long amount, Instant expiresAt) { this.orderId = orderId; this.amount = amount; this.expiresAt = expiresAt; }
    public UUID claim() { status = "creating"; leaseToken = UUID.randomUUID(); leaseUntil = Instant.now().plusSeconds(30); return leaseToken; }
    public void link(PaymentGateway.Link link) { externalId = link.externalId(); checkoutUrl = link.checkoutUrl(); if (!java.util.Set.of("paid", "review", "cancelled", "expired").contains(status)) status = "pending"; leaseUntil = null; }
    public void bind(String externalId) { this.externalId = externalId; }
    public void touch() { updatedAt = Instant.now(); }
    public void status(String value) { status = value; updatedAt = Instant.now(); leaseUntil = null; }
    public void error() { if (externalId == null && !java.util.Set.of("paid", "review", "cancelled", "expired").contains(status)) status = "error"; leaseUntil = null; updatedAt = Instant.now(); }
}
