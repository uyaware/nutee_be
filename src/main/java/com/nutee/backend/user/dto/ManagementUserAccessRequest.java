package com.nutee.backend.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ManagementUserAccessRequest(@NotNull @Pattern(regexp = "customer|staff|admin") String role,
        @NotNull Boolean isActive) {}
