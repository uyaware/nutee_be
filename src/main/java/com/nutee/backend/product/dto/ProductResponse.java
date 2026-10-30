package com.nutee.backend.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.nutee.backend.brand.dto.BrandResponse;
import com.nutee.backend.category.dto.CategoryResponse;
import com.nutee.backend.product.image.dto.ProductImageResponse;
import com.nutee.backend.product.variant.dto.ProductVariantResponse;
import com.nutee.backend.product.variant.specification.dto.VariantSpecificationResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Sản phẩm công khai cùng các quan hệ được tải theo lô. */
@Schema(name="Product")
public record ProductResponse(int id, Integer defaultVariantId, String name, String description,
        BigDecimal avgRating, int soldCount, int reviewCount, int price, int maxPrice, long stock,
        Instant updatedAt, List<BrandResponse> brands, List<CategoryResponse> categories,
        List<VariantSpecificationResponse> specifications, List<ProductVariantResponse> variants,
        List<ProductImageResponse> images) {}
