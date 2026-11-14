package com.nutee.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;

/** Mã khôi phục dùng một lần và mật khẩu mới của tài khoản. */
public record ResetPasswordRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String otp,
        @NotBlank @Size(min = 8, max = 100) @Pattern(regexp = "[\\x20-\\x7E]*", message = "Password must contain only ASCII characters.") String password) {
    /** Che mã khôi phục và mật khẩu khi dữ liệu vô tình được ghi vào log. */
    @Override
    public String toString() {
        return "ResetPasswordRequest[REDACTED]";
    }
}
