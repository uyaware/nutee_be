package com.nutee.backend.aftersale.controller;

import com.nutee.backend.aftersale.dto.*;
import com.nutee.backend.aftersale.service.AfterSaleService;
import com.nutee.backend.common.response.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** API hậu mãi của chính chủ, giữ cookie HttpOnly và CSRF hiện có. */
@RestController @RequestMapping("/api/v1/after-sales") @RequiredArgsConstructor
public class AfterSaleController {
    private final AfterSaleService service;
    @GetMapping
    public PageResponse<AfterSaleResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "all") String status, @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "") @Size(max = 100) String q) {
        return service.list(UUID.fromString(jwt.getSubject()), page, size, status, type, q);
    }
    @GetMapping("/{id}")
    public AfterSaleResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable @Min(1) int id) {
        return service.get(UUID.fromString(jwt.getSubject()), id);
    }
    @PostMapping
    public ResponseEntity<AfterSaleResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateAfterSaleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.create(UUID.fromString(jwt.getSubject()), request));
    }
}
