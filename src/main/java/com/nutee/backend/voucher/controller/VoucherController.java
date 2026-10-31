package com.nutee.backend.voucher.controller;

import com.nutee.backend.common.response.PageResponse;
import com.nutee.backend.voucher.dto.*;
import com.nutee.backend.voucher.service.VoucherService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** API voucher dành cho ADMIN còn hoạt động, giữ CSRF trên mọi request ghi. */
@RestController @RequestMapping("/api/v1/management/vouchers")
@PreAuthorize("hasRole('ADMIN') and @managementAccess.isAdmin(authentication)") @RequiredArgsConstructor
public class VoucherController {
    private final VoucherService service;
    public record ActiveRequest(@NotNull Boolean isActive) {}
    @GetMapping public PageResponse<VoucherResponse> list(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return service.list(q, status, page, size); }
    @GetMapping("/customers") public PageResponse<VoucherResponse.CustomerOption> customers(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) { return service.customers(q, page, size); }
    @GetMapping("/{id}") public VoucherResponse get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping public ResponseEntity<VoucherResponse> create(@Valid @RequestBody VoucherRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request)); }
    @PutMapping("/{id}") public VoucherResponse update(@PathVariable UUID id, @Valid @RequestBody VoucherRequest request) { return service.update(id, request); }
    @PatchMapping("/{id}/active") public VoucherResponse active(@PathVariable UUID id, @Valid @RequestBody ActiveRequest request) { return service.active(id, request.isActive()); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { service.delete(id); }
}
