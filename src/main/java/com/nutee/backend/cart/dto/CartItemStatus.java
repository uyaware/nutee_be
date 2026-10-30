package com.nutee.backend.cart.dto;

import com.fasterxml.jackson.annotation.JsonValue;

/** Khả năng mua tại thời điểm đọc giỏ, dựa trên sản phẩm và tồn kho hiện tại. */
public enum CartItemStatus {
    AVAILABLE("available"),
    HIDDEN("hidden"),
    OUT_OF_STOCK("out-of-stock"),
    INSUFFICIENT_STOCK("insufficient-stock");

    private final String value;

    CartItemStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
