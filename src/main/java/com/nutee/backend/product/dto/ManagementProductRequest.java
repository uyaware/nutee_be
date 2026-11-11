package com.nutee.backend.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ManagementProductRequest(@NotBlank @Size(max = 255) String name, String description,
        @NotNull Boolean published, Integer brandId, @NotNull @Size(max = 100) List<@NotNull Integer> categoryIds,
        Integer defaultVariantId) {}
