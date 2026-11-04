package com.nutee.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Dữ liệu đăng ký tài khoản và các ràng buộc email, username, mật khẩu. */
public record RegisterRequest(
        @Pattern(regexp = "[A-Za-z0-9_]{3,50}") String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @Size(max = 100) String fullName) {
    /** Che mật khẩu để không lộ thông tin đăng ký trong log. */
    @Override
    public String toString() {
        return "RegisterRequest[REDACTED]";
    }
}
