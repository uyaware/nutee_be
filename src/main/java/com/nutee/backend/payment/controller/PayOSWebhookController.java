package com.nutee.backend.payment.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutee.backend.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Webhook không dùng cookie; dữ liệu chỉ được xử lý sau khi adapter xác minh chữ ký. */
@RestController
@RequestMapping("/api/v1/payments/payos")
@RequiredArgsConstructor
public class PayOSWebhookController {
    private final PaymentService payments;
    private final ObjectMapper json;

    public record WebhookBody(String code, String desc, Boolean success,
            @NotNull @Size(max = 40) Map<String, Object> data,
            @NotBlank @Pattern(regexp = "[a-fA-F0-9]{64}") String signature) {
        @Override public String toString() { return "PayOSWebhook[REDACTED]"; }
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@Valid @RequestBody WebhookBody body) throws JsonProcessingException {
        payments.webhook(json.writeValueAsString(body));
        return ResponseEntity.ok().build();
    }
}
