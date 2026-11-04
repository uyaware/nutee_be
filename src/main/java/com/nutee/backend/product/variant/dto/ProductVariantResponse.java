package com.nutee.backend.product.variant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.nutee.backend.product.variant.specification.dto.VariantSpecificationResponse;
import java.util.List;

/** Biến thể với SKU, giá, tồn kho, ảnh và thông số riêng. */
@Schema(name="Variant")
public record ProductVariantResponse(int id, String sku, int price, int quantity, Integer imageId,
        List<VariantSpecificationResponse> specifications, int originalPrice, String promotionCode) {}
