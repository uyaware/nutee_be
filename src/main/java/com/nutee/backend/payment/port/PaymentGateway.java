package com.nutee.backend.payment.port;

import java.time.Instant;

/** Target của Adapter pattern: nghiệp vụ chỉ dùng các kiểu thanh toán nội bộ. */
public interface PaymentGateway {
    record Request(long orderCode, long amount, Instant expiresAt, String returnUrl, String cancelUrl, String transferContent) {}
    record Link(long orderCode, long amount, String currency, String externalId, String checkoutUrl, String qrCode,
            String bankBin, String accountNumber, String accountName, String transferContent) {
        @Override public String toString() {
            return "Link[orderCode=" + orderCode + ", amount=" + amount + ", qrCode=REDACTED]";
        }
    }
    record State(long orderCode, long amount, long amountPaid, String externalId, String status) {}
    record Event(long orderCode, long amount, String currency, String externalId, String reference, String code) {}
    boolean enabled();
    Link create(Request request);
    State retrieve(long orderCode);
    State cancel(long orderCode);
    Event verify(String rawBody);
}
