package com.nutee.backend;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.payment.adapter.*;
import org.junit.jupiter.api.Test;

class PayOSAdapterTest {
    // Public interoperability vector from payOS signature documentation, not merchant credentials.
    private final PaymentProperties properties = new PaymentProperties(true, "offline-client", "offline-api",
            "1a54716c8f0efb2744fb28b6e38b25da7f67a925d98bc1c18bd8faaecadd7675", "http://localhost:5173", 25000, 15);
    private final PayOSAdapter adapter = new PayOSAdapter(properties, new ObjectMapper());
    private final String sample = """
            {"code":"00","desc":"success","success":true,"data":{
                "orderCode":123,"amount":3000,"description":"VQRIO123","accountNumber":"12345678",
                "reference":"TF230204212323","transactionDateTime":"2023-02-04 18:25:00","currency":"VND",
                "paymentLinkId":"124c33293c43417ab7879e14c8d9eb18","code":"00","desc":"Thành công",
                "counterAccountBankId":"","counterAccountBankName":"","counterAccountName":"",
                "counterAccountNumber":"","virtualAccountName":"","virtualAccountNumber":""},
                "signature":"412e915d2871504ed31be63c8f62a149a4410d34c4c42affc9006ef9917eaa03"}
            """;

    @Test void verifiesOfficialSignatureVectorAndRejectsTamperingOrMissingSignature() {
        var event = adapter.verify(sample);
        assertThat(event.orderCode()).isEqualTo(123); assertThat(event.amount()).isEqualTo(3000);
        assertThat(event.reference()).isEqualTo("TF230204212323"); assertThat(event.code()).isEqualTo("00");
        assertThatThrownBy(() -> adapter.verify(sample.replace("3000", "3001"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> adapter.verify("{\"data\":{}}")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> adapter.verify("not-json")).isInstanceOf(ApiException.class);
    }
    @Test void browserOrOuterStatusDoesNotChangeSignedInnerData() {
        var event = adapter.verify(sample.replace("\"success\":true", "\"success\":false").replace("\"desc\":\"success\"", "\"desc\":\"forged\""));
        assertThat(event.code()).isEqualTo("00");
    }
    @Test void disabledGatewayFailsExplicitlyAndCredentialsAreRedacted() {
        var disabled = new PayOSAdapter(new PaymentProperties(false, "", "", "", "http://localhost:5173", 25000, 15), new ObjectMapper());
        assertThatThrownBy(() -> disabled.verify(sample)).isInstanceOf(ApiException.class);
        assertThat(properties.toString()).doesNotContain(properties.apiKey(), properties.checksumKey());
        assertThatThrownBy(() -> new PayOSAdapter(new PaymentProperties(true, "", "", "", "http://localhost:5173", 25000, 15), new ObjectMapper())).isInstanceOf(IllegalArgumentException.class);
    }
}
