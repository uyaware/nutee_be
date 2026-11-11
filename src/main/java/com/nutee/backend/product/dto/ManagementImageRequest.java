package com.nutee.backend.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ManagementImageRequest(@NotBlank @Pattern(regexp = "^(https?://.+|/(?!/).+)$") String url,
        @NotNull Boolean primary) {}
