package com.nutee.backend.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Cấu hình Secure và SameSite được dùng chung cho các cookie xác thực. */
@Validated
@ConfigurationProperties("app.security.cookies")
public record CookieProperties(boolean secure, @Pattern(regexp = "Strict|Lax|None") String sameSite) {
    /** Chỉ cho dùng SameSite=None khi cookie cũng có Secure. */
    @AssertTrue(message = "SameSite=None requires secure cookies and HTTPS")
    public boolean isSameSiteValid() {
        return !"None".equals(sameSite) || secure;
    }
}
