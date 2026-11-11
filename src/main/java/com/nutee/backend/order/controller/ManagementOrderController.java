package com.nutee.backend.order.controller;

import com.nutee.backend.common.response.PageResponse;
import com.nutee.backend.order.dto.ManagementOrderHistoryResponse;
import com.nutee.backend.order.dto.OrderResponse;
import com.nutee.backend.order.service.ManagementOrderService;
import com.nutee.backend.payment.service.PaymentService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/management/orders")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF') and @managementAccess.isStaffOrAdmin(authentication)")
@RequiredArgsConstructor
public class ManagementOrderController {
    private final ManagementOrderService service;
    private final PaymentService payments;

    @GetMapping
    public PageResponse<OrderResponse> list(@RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(defaultValue = "all") String status, @RequestParam(defaultValue = "all") String payment,
            @RequestParam(defaultValue = "newest") String sort, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(q, status, payment, sort, page, size);
    }

    @GetMapping("/{id}") public OrderResponse get(@PathVariable @Min(1) int id) { return service.get(id); }
    @GetMapping("/{id}/history") public List<ManagementOrderHistoryResponse> history(@PathVariable @Min(1) int id) { return service.history(id); }

    public record StatusRequest(@NotBlank @Pattern(regexp = "confirmed|packing|shipping|delivered|cancelled") String status) {}
    @PostMapping("/{id}/status")
    public OrderResponse transition(@PathVariable @Min(1) int id, @Valid @RequestBody StatusRequest request, Authentication authentication) {
        UUID actor = UUID.fromString(authentication.getName());
        if ("cancelled".equals(request.status()) && "qr".equals(service.get(id).paymentMethod())) {
            payments.cancelManagementQr(actor, id);
            return service.get(id);
        }
        return service.transition(actor, id, request.status());
    }
}
