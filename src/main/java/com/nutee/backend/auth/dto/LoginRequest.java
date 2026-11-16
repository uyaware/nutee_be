package com.nutee.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;

/** Đăng nhập bằng email; giữ tên identifier để tương thích hợp đồng API. */
public record LoginRequest(@NotBlank @Email @Size(max = 255) String identifier,
        @NotBlank @Size(max = 100) String password) {
    public LoginRequest {
        if (identifier != null) identifier = identifier.strip();
    }
    /** Che mật khẩu để không lộ thông tin đăng nhập trong log. */
    @Override
    public String toString() {
        return "LoginRequest[REDACTED]";
    }
}
