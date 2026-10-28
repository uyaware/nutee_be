package com.nutee.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = 255) String identifier,
        @NotBlank @Size(max = 72) String password) {
    @Override
    public String toString() { return "LoginRequest[REDACTED]"; }
}
