package com.nutee.backend.cart.dto;

import com.nutee.backend.product.variant.specification.dto.VariantSpecificationResponse;
import java.util.List;

/** Dữ liệu biến thể và giá hiện tại; giá chưa xác định được giữ null thay vì giả định miễn phí. */
public record CartItemResponse(int variantId, int productId, String sku, String name,
        String imageUrl, Integer price, int quantity, int stock, CartItemStatus status,
        Long lineTotal, List<VariantSpecificationResponse> specifications, Integer originalPrice, String promotionCode) {}
