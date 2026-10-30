package com.nutee.backend.cart.dto;

import com.nutee.backend.cart.entity.CartItem;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Số lượng tuyệt đối thay cho số lượng hiện tại; xóa dòng dùng DELETE. */
public record CartQuantityRequest(@NotNull @Min(1) @Max(CartItem.MAX_QUANTITY) Integer quantity) {}
