package com.nutee.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleLoginRequest(@NotBlank @Size(max = 16384) String idToken) {
    @Override
    public String toString() { return "GoogleLoginRequest[REDACTED]"; }
}
