package com.nutee.backend.aftersale.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Thông tin yêu cầu cùng snapshot sản phẩm và tiến trình có thật trong database. */
public record AfterSaleResponse(int id, int orderId, String orderCode, int orderItemId, UUID userId,
        String type, int quantity, String reason, String description, String status, Instant createdAt,
        Item item, Contact customer, List<History> history) {
    public record Item(String id, String productId, String name, String imageUrl, String configuration,
            int quantity, BigDecimal price, Instant warrantyExpiresAt) {}
    public record Contact(String name, String phone, String addressLine) {}
    public record History(String status, Instant at, UUID actorId, String actorName) {}
}
