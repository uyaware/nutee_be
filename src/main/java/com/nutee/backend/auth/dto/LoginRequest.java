package com.nutee.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Thông tin đăng nhập bằng email/username và lựa chọn ghi nhớ phiên. */
public record LoginRequest(@NotBlank @Size(max = 255) String identifier,
        @NotBlank @Size(max = 72) String password, boolean remember) {
    public LoginRequest(String identifier, String password) {
        this(identifier, password, false);
    }
    /** Che mật khẩu để không lộ thông tin đăng nhập trong log. */
    @Override
    public String toString() {
        return "LoginRequest[REDACTED]";
    }
}
