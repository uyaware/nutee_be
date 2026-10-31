package com.nutee.backend.payment.entity;

import com.nutee.backend.payment.port.PaymentGateway;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Audit tối thiểu và khóa chống replay; không lưu raw payload, bank account hoặc khóa/chữ ký. */
@Entity @Table(name = "payment_events") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEvent {
    @Id private String id;
    @Column(name = "gateway_order_code") private long gatewayOrderCode;
    @Column(name = "external_id") private String externalId;
    private long amount;
    private String currency;
    @Column(name = "result_code") private String resultCode;
    private String decision;
    @Column(name = "received_at") private Instant receivedAt;
    public PaymentEvent(PaymentGateway.Event event, String decision) {
        id = "payos:" + event.reference(); gatewayOrderCode = event.orderCode(); externalId = event.externalId(); amount = event.amount();
        currency = event.currency(); resultCode = event.code(); this.decision = decision; receivedAt = Instant.now();
    }
    public boolean matches(PaymentGateway.Event event) { return gatewayOrderCode == event.orderCode() && amount == event.amount()
            && java.util.Objects.equals(externalId, event.externalId()) && java.util.Objects.equals(currency, event.currency()) && java.util.Objects.equals(resultCode, event.code()); }
}
