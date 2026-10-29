package com.nutee.backend.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.validation.annotation.Validated;

/** Cấu hình tập trung cho khóa ký và thời hạn của hai loại JWT. */
@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        String accessSecret, String refreshSecret,
        @NotNull @DurationMin(seconds = 1) Duration accessTokenTtl,
        @NotNull @DurationMin(seconds = 1) Duration refreshTokenTtl) {

    /** Kiểm tra hai khóa Base64 đủ mạnh và khác nhau để tách access với refresh. */
    @AssertTrue(message = "JWT_ACCESS_SECRET and JWT_REFRESH_SECRET must be distinct Base64 keys of at least 32 bytes")
    public boolean isKeyConfigurationValid() {
        try {
            byte[] access = Base64.getDecoder().decode(accessSecret == null ? "" : accessSecret);
            byte[] refresh = Base64.getDecoder().decode(refreshSecret == null ? "" : refreshSecret);
            return access.length >= 32 && refresh.length >= 32 && !MessageDigest.isEqual(access, refresh);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** Refresh token phải sống lâu hơn access token để có thể gia hạn phiên. */
    @AssertTrue(message = "Refresh token lifetime must exceed access token lifetime")
    public boolean isRefreshLifetimeLonger() {
        return accessTokenTtl == null || refreshTokenTtl == null || refreshTokenTtl.compareTo(accessTokenTtl) > 0;
    }

    /** Che các khóa ký khi cấu hình được ghi vào log. */
    @Override
    public String toString() { return "JwtProperties[secrets=REDACTED]"; }
}
