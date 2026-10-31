package com.nutee.backend.order.dto;

import java.time.Instant;
import java.util.List;
import java.math.BigDecimal;

/** Snapshot đơn hàng thuộc chính chủ, không lấy trạng thái từ URL hoặc dữ liệu giỏ hiện tại. */
public record OrderResponse(int id, String code, String status, String paymentMethod, String paymentStatus,
        BigDecimal subtotal, long promotionDiscount, long voucherDiscount, BigDecimal discount, int shipping, BigDecimal total,
        Integer addressId, String recipient, String phone, String addressLine, String note, Instant createdAt, Instant paymentExpiresAt,
        List<Item> items) {
    public record Item(int id, int productId, int variantId, String name, String sku, String imageUrl,
            String configuration, int quantity, BigDecimal price, BigDecimal originalPrice) {}
}
