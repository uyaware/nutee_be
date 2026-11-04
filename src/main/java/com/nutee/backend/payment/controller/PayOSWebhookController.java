package com.nutee.backend.payment.controller;

import com.nutee.backend.payment.service.PaymentService;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

/** Webhook không dùng cookie; dữ liệu chỉ được xử lý sau khi adapter xác minh chữ ký. */
@RestController
@RequestMapping("/api/v1/payments/payos")
@RequiredArgsConstructor
public class PayOSWebhookController {
    private final PaymentService payments;

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody @Size(max = 131072) String body, @RequestHeader HttpHeaders headers) {
        payments.webhook(body, headers);
        return ResponseEntity.ok().build();
    }
}
