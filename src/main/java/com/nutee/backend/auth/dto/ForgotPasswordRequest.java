package com.nutee.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Email cần gửi liên kết khôi phục mật khẩu. */
public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 255) String email) {}
