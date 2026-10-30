package com.nutee.backend.cart.dto;

import com.nutee.backend.cart.entity.CartItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Số lượng cần cộng vào dòng biến thể đang có, hoặc tạo dòng mới. */
public record AddCartItemRequest(
        @NotNull @Min(1) Integer variantId,
        @NotNull @Min(1) @Max(CartItem.MAX_QUANTITY) Integer quantity) {}
