package com.nutee.backend.payment.adapter;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Khóa chỉ nằm ở backend; không sinh credential giả khi kênh chưa được cấu hình. */
@ConfigurationProperties("app.payment") @Validated
public record PaymentProperties(boolean enabled, String clientId, String apiKey, String checksumKey,
        @NotBlank String frontendUrl, @Min(0) @Max(1000000000) long shippingFee,
        @Min(5) @Max(60) int expiryMinutes) {
    @Override public String toString() { return "PaymentProperties[credentials=REDACTED]"; }
}
