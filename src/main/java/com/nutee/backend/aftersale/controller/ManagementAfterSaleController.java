package com.nutee.backend.aftersale.controller;

import com.nutee.backend.aftersale.dto.AfterSaleResponse;
import com.nutee.backend.aftersale.service.AfterSaleService;
import com.nutee.backend.common.response.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Nhân viên hoặc admin đang hoạt động tiếp nhận và xử lý hậu mãi, không tự hoàn tiền. */
@RestController @RequestMapping("/api/v1/management/after-sales")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF') and @managementAccess.isStaffOrAdmin(authentication)")
@RequiredArgsConstructor
public class ManagementAfterSaleController {
    private final AfterSaleService service;
    @GetMapping
    public PageResponse<AfterSaleResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "all") String status, @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "") @Size(max = 100) String q) {
        return service.list(null, page, size, status, type, q);
    }
    @GetMapping("/{id}") public AfterSaleResponse get(@PathVariable @Min(1) int id) { return service.get(null, id); }
    public record StatusRequest(@NotBlank String expectedStatus,
            @NotBlank @Pattern(regexp = "reviewing|approved|rejected|processing|completed") String status) {}
    @PostMapping("/{id}/status")
    public AfterSaleResponse transition(Authentication authentication, @PathVariable @Min(1) int id, @Valid @RequestBody StatusRequest request) {
        return service.transition(UUID.fromString(authentication.getName()), id, request.expectedStatus(), request.status());
    }
}
