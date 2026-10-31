package com.nutee.backend.payment.entity;

import com.nutee.backend.payment.port.PaymentGateway;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Mỗi dòng lưu một lần nhận webhook; trạng thái thanh toán chỉ nằm trên orders. */
@Entity @Table(name = "transaction_logs") @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(name = "order_id") private Integer orderId;
    @Column(name = "gateway_order_code") private long gatewayOrderCode;
    private String provider = "payos";
    private long amount;
    private String currency;
    private String reference;
    @Column(name = "result_code") private String resultCode;
    private String status;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "raw_body", columnDefinition = "jsonb") private Map<String, Object> rawBody;
    @Column(name = "request_header", columnDefinition = "text") private String requestHeader;
    @Column(name = "created_at", updatable = false) private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    public TransactionLog(Integer orderId, PaymentGateway.Event event, Map<String, Object> body, String headers, String decision) {
        this.orderId = orderId;
        gatewayOrderCode = event.orderCode();
        amount = event.amount();
        currency = event.currency();
        reference = "payos:" + event.reference();
        resultCode = event.code();
        rawBody = body;
        requestHeader = headers;
        status = decision;
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    /** So sánh dữ liệu đã ký khi cùng reference được gửi lại, kể cả log chuyển từ schema cũ. */
    public boolean matches(PaymentGateway.Event event) {
        if (gatewayOrderCode != event.orderCode() || amount != event.amount()
                || !Objects.equals(currency, event.currency()) || !Objects.equals(resultCode, event.code())) return false;
        return rawBody == null || !(rawBody.get("data") instanceof Map<?, ?> data)
                || Objects.equals(data.get("paymentLinkId"), event.externalId());
    }
}
