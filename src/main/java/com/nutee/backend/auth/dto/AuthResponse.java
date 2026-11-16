package com.nutee.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** Kết quả xác thực; JWT chỉ dùng để đặt cookie, không được xuất vào JSON. */
public record AuthResponse(
        @JsonIgnore String accessToken,
        @JsonIgnore String refreshToken,
        long expiresIn,
        long refreshExpiresIn,
        UserResponse user,
        boolean verificationRequired,
        VerificationResponse verification,
        @JsonIgnore String verificationToken) {

    public AuthResponse(String accessToken, String refreshToken, long expiresIn,
            long refreshExpiresIn, UserResponse user) {
        this(accessToken, refreshToken, expiresIn, refreshExpiresIn, user, false, null, null);
    }

    /** Trạng thái chờ không mang thông tin vai trò và không có access/refresh token. */
    public static AuthResponse pending(String token, VerificationResponse verification) {
        return new AuthResponse(null, null, 0, 0, null, true, verification, token);
    }

    /** Che token khi kết quả xác thực vô tình được ghi vào log. */
    @Override
    public String toString() {
        return "AuthResponse[REDACTED]";
    }
}
