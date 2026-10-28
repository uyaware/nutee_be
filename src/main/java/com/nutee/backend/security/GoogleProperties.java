package com.nutee.backend.security;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.security.google")
public record GoogleProperties(boolean enabled, String clientId, boolean secureCookie) {
    @AssertTrue(message = "Set GOOGLE_CLIENT_ID before enabling GOOGLE_AUTH_ENABLED")
    public boolean isClientConfigured() { return !enabled || StringUtils.hasText(clientId); }
}
