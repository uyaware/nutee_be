package com.nutee.backend.category.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Danh mục công khai; parentId bằng null khi là danh mục gốc. */
@Schema(name="Category")
public record CategoryResponse(int id, String name, Integer parentId) {}
