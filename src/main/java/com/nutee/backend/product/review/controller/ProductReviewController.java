package com.nutee.backend.product.review.controller;

import com.nutee.backend.product.review.dto.*;
import com.nutee.backend.product.review.service.ProductReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** API đánh giá chính chủ theo dòng hàng; mọi thao tác ghi giữ CSRF hiện có. */
@RestController @RequestMapping("/api/v1/orders/{orderId}/items/{itemId}/review") @RequiredArgsConstructor
public class ProductReviewController {
    private final ProductReviewService service;
    @GetMapping
    public ResponseEntity<OwnReviewResponse> get(@AuthenticationPrincipal Jwt jwt,
            @PathVariable @Min(1) int orderId, @PathVariable @Min(1) int itemId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(UUID.fromString(jwt.getSubject()), orderId, itemId));
    }
    @PutMapping
    public ResponseEntity<OwnReviewResponse> save(@AuthenticationPrincipal Jwt jwt,
            @PathVariable @Min(1) int orderId, @PathVariable @Min(1) int itemId, @Valid @RequestBody SaveReviewRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.save(UUID.fromString(jwt.getSubject()), orderId, itemId, request));
    }
}
