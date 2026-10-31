package com.nutee.backend.payment.port;

import java.time.Instant;

/** Target của Adapter pattern: nghiệp vụ chỉ dùng các kiểu thanh toán nội bộ. */
public interface PaymentGateway {
    record Request(long orderCode, long amount, Instant expiresAt, String returnUrl, String cancelUrl) {}
    record Link(long orderCode, long amount, String currency, String externalId, String checkoutUrl) {}
    record State(long orderCode, long amount, long amountPaid, String externalId, String status) {}
    record Event(long orderCode, long amount, String currency, String externalId, String reference, String code) {}
    boolean enabled();
    Link create(Request request);
    State retrieve(long orderCode);
    State cancel(long orderCode);
    Event verify(String rawBody);
}
