package com.nutee.backend.security;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import jakarta.validation.constraints.NotBlank;
import java.security.MessageDigest;
import java.util.Base64;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(@NotBlank String issuer, @NotBlank String audience,
        String accessSecret, String refreshSecret) {

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

    @Override
    public String toString() { return "JwtProperties[secrets=REDACTED]"; }
}
