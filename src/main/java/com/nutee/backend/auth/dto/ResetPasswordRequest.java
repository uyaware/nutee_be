package com.nutee.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Mã khôi phục dùng một lần và mật khẩu mới của tài khoản. */
public record ResetPasswordRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
        @NotBlank @Size(min = 12, max = 72) String password) {
    /** Che mã khôi phục và mật khẩu khi dữ liệu vô tình được ghi vào log. */
    @Override
    public String toString() {
        return "ResetPasswordRequest[REDACTED]";
    }
}
