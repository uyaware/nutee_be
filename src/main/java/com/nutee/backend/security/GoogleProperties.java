package com.nutee.backend.security;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/** Bật/tắt Google và khai báo client ID được phép đăng nhập. */
@Validated
@ConfigurationProperties("app.security.google")
public record GoogleProperties(boolean enabled, String clientId) {
    /** Không cho bật Google nếu chưa có OAuth client ID. */
    @AssertTrue(message = "Set GOOGLE_CLIENT_ID before enabling GOOGLE_AUTH_ENABLED")
    public boolean isClientConfigured() { return !enabled || StringUtils.hasText(clientId); }
}
