package com.nutee.backend.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManagementTaxonomyRequest(@NotBlank @Size(max = 80) String name, Integer parentId) {}
