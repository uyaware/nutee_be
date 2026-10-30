package com.nutee.backend.cart.dto;

import java.util.List;
import java.util.UUID;

/** Tổng số lượng gồm mọi dòng; subtotal chỉ tính các dòng hiện đủ điều kiện mua. */
public record CartResponse(UUID ownerId, List<CartItemResponse> items, long quantity, long subtotal,
        boolean hasIssues) {}
