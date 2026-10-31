package com.nutee.backend.promotion.controller;

import com.nutee.backend.common.response.PageResponse;
import com.nutee.backend.promotion.dto.PromotionRequest;
import com.nutee.backend.promotion.dto.PromotionResponse;
import com.nutee.backend.promotion.service.PromotionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** API quản trị promotion; chỉ ADMIN còn hoạt động được đọc hoặc thay đổi rule. */
@RestController
@RequestMapping("/api/v1/management/promotions")
@PreAuthorize("hasRole('ADMIN') and @managementAccess.isAdmin(authentication)")
@RequiredArgsConstructor
public class PromotionController {
    private final PromotionService service;
    public record ActiveRequest(@NotNull Boolean isActive) {}

    @GetMapping
    public PageResponse<PromotionResponse> list(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "all") String status, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) { return service.list(q, status, page, size); }
    @GetMapping("/products")
    public PageResponse<PromotionResponse.ProductOption> products(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) { return service.products(q, page, size); }
    @GetMapping("/{id}") public PromotionResponse get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping public ResponseEntity<PromotionResponse> create(@Valid @RequestBody PromotionRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request)); }
    @PutMapping("/{id}") public PromotionResponse update(@PathVariable UUID id, @Valid @RequestBody PromotionRequest request) { return service.update(id, request); }
    @PatchMapping("/{id}/active") public PromotionResponse active(@PathVariable UUID id, @Valid @RequestBody ActiveRequest request) { return service.active(id, request.isActive()); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { service.delete(id); }
}
