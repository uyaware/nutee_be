package com.nutee.backend.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configurable access lifetime and absolute refresh-token lifetime. */
@Validated
@ConfigurationProperties("app.security.tokens")
public record TokenProperties(
        @NotNull @DurationMin(seconds = 1) Duration accessTokenTtl,
        @NotNull @DurationMin(seconds = 1) Duration refreshTokenTtl) {

    @AssertTrue(message = "Refresh token lifetime must exceed access token lifetime")
    public boolean isRefreshLifetimeLonger() {
        return accessTokenTtl == null || refreshTokenTtl == null
                || refreshTokenTtl.compareTo(accessTokenTtl) > 0;
    }
}
