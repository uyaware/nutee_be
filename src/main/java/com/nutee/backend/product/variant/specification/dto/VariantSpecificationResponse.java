package com.nutee.backend.product.variant.specification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Tên và giá trị thông số kỹ thuật của một biến thể. */
@Schema(name="Specification")
public record VariantSpecificationResponse(String name, String value) {}
