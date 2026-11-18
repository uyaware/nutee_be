package com.nutee.backend.promotion.dto;

import com.nutee.backend.common.discount.Discounts;
import com.nutee.backend.promotion.entity.Promotion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Rule quản trị và các sản phẩm được tải theo lô sau phân trang. */
public record PromotionResponse(UUID id, String code, String type, int value, Instant startsAt,
        Instant endsAt, boolean isActive, String status, String scope, List<ProductOption> products, List<Integer> productIds) {
    public record ProductOption(int id, String name, boolean published, String imageUrl, Integer minPrice, Integer maxPrice,
            UUID promotionId, String promotionCode) {}

    public static PromotionResponse from(Promotion rule, List<ProductOption> products) {
        return new PromotionResponse(rule.getId(), rule.getCode(), rule.getType(), rule.getValue(),
                rule.getStartsAt(), rule.getEndsAt(), rule.isActive(),
                Discounts.status(rule.isActive(), rule.getStartsAt(), rule.getEndsAt(), Instant.now()), rule.getScope(),
                products, products.stream().map(ProductOption::id).toList());
    }
}
