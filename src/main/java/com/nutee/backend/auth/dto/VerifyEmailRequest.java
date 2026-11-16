package com.nutee.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Mã OTP chỉ dùng cùng cookie chờ xác thực đã được ký. */
public record VerifyEmailRequest(@NotBlank @Pattern(regexp = "[0-9]{6}") String otp) {
    @Override
    public String toString() {
        return "VerifyEmailRequest[REDACTED]";
    }
}
