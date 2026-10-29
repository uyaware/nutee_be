package com.nutee.backend.auth.service;

import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.security.GoogleProperties;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/** Strategy Google: xác minh ID token bằng khóa công khai của Google và nonce trình duyệt. */
@Service
public class GoogleTokenVerifier implements SocialLoginProvider {
    private final GoogleProperties properties;
    private final JwtDecoder decoder;

    /** Dùng địa chỉ khóa công khai chính thức khi chạy ứng dụng. */
    @Autowired
    public GoogleTokenVerifier(GoogleProperties properties) {
        this(properties, "https://www.googleapis.com/oauth2/v3/certs");
    }

    /** Cho phép dùng máy chủ khóa cục bộ khi kiểm tra chữ ký trong test. */
    GoogleTokenVerifier(GoogleProperties properties, String jwkSetUri) {
        this.properties = properties;
        if (properties.enabled()) {
            NimbusJwtDecoder google = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                    .jwsAlgorithm(SignatureAlgorithm.RS256).build();
            google.setJwtValidator(jwt -> validClaims(jwt, properties.clientId())
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token")));
            decoder = google;
        } else { decoder = null; }
    }

    /** Mã lưu liên kết tài khoản Google trong cơ sở dữ liệu. */
    @Override
    public String name() { return "GOOGLE"; }

    /** Từ chối đăng nhập Google khi tính năng chưa được cấu hình và bật. */
    @Override
    public void requireEnabled() {
        if (!properties.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Google sign-in is not configured. Set GOOGLE_CLIENT_ID and GOOGLE_AUTH_ENABLED.");
        }
    }

    /** Xác minh credential, nonce và trả về danh tính chung cho mọi provider. */
    @Override
    public SocialIdentity verify(String token, String nonce) {
        requireEnabled();
        try {
            Jwt jwt = decoder.decode(token);
            if (nonce == null || !nonce.equals(jwt.getClaimAsString("nonce"))) {
                throw new BadCredentialsException("Invalid Google nonce.");
            }
            String name = jwt.getClaimAsString("name");
            if (name != null && name.length() > 100) { name = name.substring(0, 100); }
            return new SocialIdentity(jwt.getSubject(), jwt.getClaimAsString("email"), name,
                    jwt.getClaimAsString("picture"));
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BadCredentialsException("Invalid Google identity.");
        }
    }

    /** Chỉ nhận token từ Google, đúng client ID, còn hạn và có email đã xác minh. */
    static boolean validClaims(Jwt jwt, String clientId) {
        try {
            String issuer = jwt.getClaimAsString("iss");
            String email = jwt.getClaimAsString("email");
            String authorizedParty = jwt.getClaimAsString("azp");
            return Set.of("accounts.google.com", "https://accounts.google.com").contains(issuer)
                    && jwt.getAudience().contains(clientId)
                    && (authorizedParty == null || clientId.equals(authorizedParty))
                    && jwt.getSubject() != null && !jwt.getSubject().isBlank() && jwt.getSubject().length() <= 255
                    && email != null && email.length() <= 255 && email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                    && Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))
                    && jwt.getExpiresAt() != null && jwt.getExpiresAt().isAfter(Instant.now())
                    && jwt.getIssuedAt() != null && !jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(30))
                    && (jwt.getNotBefore() == null || !jwt.getNotBefore().isAfter(Instant.now()));
        } catch (RuntimeException exception) { return false; }
    }
}
