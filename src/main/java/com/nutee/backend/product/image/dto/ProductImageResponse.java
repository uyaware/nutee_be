package com.nutee.backend.product.image.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Ảnh thuộc sản phẩm và cờ ảnh chính. */
@Schema(name="Image")
public record ProductImageResponse(int id, String url, boolean primary) {}
