package com.nutee.backend.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ManagementVariantRequest(@NotBlank @Size(max = 16) String sku, @NotNull @Min(0) Integer price,
        @NotNull @Min(0) Integer quantity, @Min(0) Integer expectedQuantity, Integer imageId,
        @NotNull @Size(max = 100) List<@NotNull @Valid Spec> specifications) {
    public record Spec(@NotBlank @Size(max = 255) String name, @NotBlank String value) {}
}
