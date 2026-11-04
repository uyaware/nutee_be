package com.nutee.backend.order.controller;

import com.nutee.backend.common.response.PageResponse;
import com.nutee.backend.order.dto.*;
import com.nutee.backend.order.service.OrderService;
import com.nutee.backend.order.service.CheckoutService;
import com.nutee.backend.payment.adapter.PaymentProperties;
import com.nutee.backend.payment.port.PaymentGateway;
import com.nutee.backend.payment.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** API đơn và thanh toán của chính chủ; mọi thao tác ghi dùng cookie và CSRF hiện tại. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orders;
    private final CheckoutService checkout;
    private final PaymentService payments;
    private final PaymentTransactions transactions;
    private final PaymentProperties properties;
    private final PaymentGateway gateway;

    @GetMapping("/checkout/config")
    public Map<String, Object> config() {
        return Map.of("payosEnabled", gateway.enabled(), "shippingFee", properties.shippingFee());
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(checkout.create(UUID.fromString(jwt.getSubject()), request));
    }

    @GetMapping("/orders")
    public PageResponse<OrderResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "") @Size(max = 100) String q) {
        return orders.list(UUID.fromString(jwt.getSubject()), page, size, status, q);
    }

    @GetMapping("/orders/{id}")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) int id) {
        return orders.get(UUID.fromString(jwt.getSubject()), id);
    }

    @PostMapping("/orders/{id}/payment")
    public ResponseEntity<PaymentTransactions.View> pay(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) int id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(payments.create(UUID.fromString(jwt.getSubject()), id));
    }

    @GetMapping("/orders/{id}/payment")
    public ResponseEntity<PaymentTransactions.View> payment(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) int id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(payments.get(UUID.fromString(jwt.getSubject()), id));
    }

    @PostMapping("/orders/{id}/payment/refresh")
    public ResponseEntity<PaymentTransactions.View> refresh(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) int id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(payments.refresh(UUID.fromString(jwt.getSubject()), id));
    }

    @PostMapping("/orders/{id}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) int id) {
        UUID owner = UUID.fromString(jwt.getSubject());
        var order = orders.get(owner, id);
        if ("cod".equals(order.paymentMethod())) {
            transactions.cancelCod(owner, id);
        } else {
            payments.cancel(owner, id);
        }
        return orders.get(owner, id);
    }
}
