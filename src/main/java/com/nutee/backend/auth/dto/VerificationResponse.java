package com.nutee.backend.auth.dto;

/** Thông tin hiển thị khi chờ xác thực; không cấp quyền đăng nhập. */
public record VerificationResponse(String email, long expiresIn, long resendAfter,
        boolean mailSent, String message) {}
