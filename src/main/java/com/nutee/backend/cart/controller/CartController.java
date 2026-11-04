package com.nutee.backend.cart.controller;

import com.nutee.backend.cart.dto.AddCartItemRequest;
import com.nutee.backend.cart.dto.CartQuantityRequest;
import com.nutee.backend.cart.dto.CartResponse;
import com.nutee.backend.cart.service.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API giỏ hàng của tài khoản trong access JWT; không nhận userId do client chỉ định. */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService service;

    @GetMapping
    public ResponseEntity<CartResponse> get(@AuthenticationPrincipal Jwt jwt) {
        return response(service.get(UUID.fromString(jwt.getSubject())));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> add(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddCartItemRequest request) {
        return response(service.add(UUID.fromString(jwt.getSubject()), request.variantId(), request.quantity()));
    }

    @PutMapping("/items/{variantId}")
    public ResponseEntity<CartResponse> setQuantity(@AuthenticationPrincipal Jwt jwt,
            @PathVariable @Min(1) int variantId, @Valid @RequestBody CartQuantityRequest request) {
        return response(service.setQuantity(UUID.fromString(jwt.getSubject()), variantId, request.quantity()));
    }

    @DeleteMapping("/items/{variantId}")
    public ResponseEntity<CartResponse> remove(@AuthenticationPrincipal Jwt jwt,
            @PathVariable @Min(1) int variantId) {
        return response(service.remove(UUID.fromString(jwt.getSubject()), variantId));
    }

    @DeleteMapping
    public ResponseEntity<CartResponse> clear(@AuthenticationPrincipal Jwt jwt) {
        return response(service.clear(UUID.fromString(jwt.getSubject())));
    }

    private ResponseEntity<CartResponse> response(CartResponse cart) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cart);
    }
}
