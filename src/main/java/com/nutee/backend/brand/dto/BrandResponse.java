package com.nutee.backend.brand.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Thương hiệu công khai của sản phẩm. */
@Schema(name="Brand")
public record BrandResponse(int id, String name) {}
