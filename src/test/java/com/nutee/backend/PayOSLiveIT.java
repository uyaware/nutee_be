package com.nutee.backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutee.backend.payment.adapter.PayOSAdapter;
import com.nutee.backend.payment.adapter.PaymentProperties;
import com.nutee.backend.payment.port.PaymentGateway;
import java.security.SecureRandom;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Kiểm tra kênh merchant thật theo lựa chọn; chỉ tạo rồi hủy link, không chuyển tiền. */
@EnabledIfEnvironmentVariable(named = "PAYOS_LIVE_TEST_ENABLED", matches = "true")
class PayOSLiveIT {
    @Test
    void merchantCreatesRetrievesAndCancelsAnUnpaidLink() {
        var properties = new PaymentProperties(true, System.getenv("PAYOS_CLIENT_ID"),
                System.getenv("PAYOS_API_KEY"), System.getenv("PAYOS_CHECKSUM_KEY"),
                "http://localhost:5173", 25000, 5);
        var adapter = new PayOSAdapter(properties, new ObjectMapper());
        long code = 9_000_000_000L + new SecureRandom().nextInt(900_000_000);
        String returnUrl = properties.frontendUrl() + "/orders/0/payment";
        var link = adapter.create(new PaymentGateway.Request(code, 2000,
                Instant.now().plusSeconds(300), returnUrl, returnUrl, "LIVE" + code));
        try {
            assertThat(link.orderCode()).isEqualTo(code);
            assertThat(link.amount()).isEqualTo(2000);
            assertThat(link.currency()).isEqualTo("VND");
            assertThat(link.qrCode()).startsWith("000201");
            assertThat(link.transferContent()).isEqualTo("LIVE" + code);
            assertThat(link.accountNumber()).isNotBlank();
            assertThat(link.accountName()).isNotBlank();
            assertThat(link.checkoutUrl()).startsWith("https://pay.payos.vn/");
            var state = adapter.retrieve(code);
            assertThat(state.externalId()).isEqualTo(link.externalId());
            assertThat(state.amountPaid()).isZero();
            assertThat(state.status()).isEqualTo("PENDING");
        } finally {
            var cancelled = adapter.cancel(code);
            assertThat(cancelled.amountPaid()).isZero();
            assertThat(cancelled.status()).isEqualTo("CANCELLED");
        }
    }
}
