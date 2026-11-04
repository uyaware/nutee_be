package com.nutee.backend.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** Kết quả xác thực; JWT chỉ dùng để đặt cookie, không được xuất vào JSON. */
public record AuthResponse(
        @JsonIgnore String accessToken,
        @JsonIgnore String refreshToken,
        long expiresIn,
        long refreshExpiresIn,
        UserResponse user) {

    /** Che token khi kết quả xác thực vô tình được ghi vào log. */
    @Override
    public String toString() {
        return "AuthResponse[REDACTED]";
    }
}
