package com.nutee.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Credential đăng nhập bên thứ ba; vẫn nhận idToken để tương thích frontend Google hiện tại. */
public record SocialLoginRequest(@NotBlank @Size(max = 10000) @JsonAlias("idToken") String credential) {

    /** Không ghi credential của provider vào log. */
    @Override
    public String toString() {
        return "SocialLoginRequest[REDACTED]";
    }
}
