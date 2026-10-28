package com.nutee.backend.auth.dto;

public record TokenResponse(String accessToken, String refreshToken, String tokenType,
        long expiresIn, long refreshExpiresIn, UserResponse user) {
    @Override
    public String toString() { return "TokenResponse[REDACTED]"; }
}
