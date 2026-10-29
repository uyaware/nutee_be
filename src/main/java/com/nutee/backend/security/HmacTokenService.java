package com.nutee.backend.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nutee.backend.user.entity.User;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

@Service
public class HmacTokenService {
    private final TokenProperties lifetimes;
    private final byte[] accessKey;
    private final byte[] refreshKey;
    private final JwtDecoder accessDecoder;
    private final JwtDecoder refreshDecoder;

    public HmacTokenService(JwtProperties properties, TokenProperties lifetimes) {
        if (!properties.isKeyConfigurationValid()) {
            throw new IllegalArgumentException("Distinct Base64 HMAC keys of at least 32 bytes are required.");
        }
        this.lifetimes = lifetimes;
        accessKey = Base64.getDecoder().decode(properties.accessSecret());
        refreshKey = Base64.getDecoder().decode(properties.refreshSecret());
        accessDecoder = decoder(accessKey, "access");
        refreshDecoder = decoder(refreshKey, "refresh");
    }

    public IssuedTokens issue(User user) {
        return issue(user, Instant.now().plus(lifetimes.refreshTokenTtl()));
    }

    public IssuedTokens issue(User user, Instant refreshExpiresAt) {
        Instant now = Instant.now();
        Instant accessExpiresAt = now.plus(lifetimes.accessTokenTtl());
        if (accessExpiresAt.isAfter(refreshExpiresAt)) {
            accessExpiresAt = refreshExpiresAt;
        }
        return new IssuedTokens(sign(user, "access", now, accessExpiresAt, accessKey),
                sign(user, "refresh", now, refreshExpiresAt, refreshKey),
                Math.max(0, accessExpiresAt.getEpochSecond() - now.getEpochSecond()),
                Math.max(0, refreshExpiresAt.getEpochSecond() - now.getEpochSecond()));
    }

    public Jwt decodeAccess(String token) { return accessDecoder.decode(token); }
    public Jwt decodeRefresh(String token) { return refreshDecoder.decode(token); }

    private String sign(User user, String kind, Instant now, Instant expiresAt, byte[] key) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().subject(user.getId().toString())
                .jwtID(UUID.randomUUID().toString()).issueTime(Date.from(now))
                .notBeforeTime(Date.from(now)).expirationTime(Date.from(expiresAt))
                .claim("email", user.getEmail()).claim("token_type", kind);
        if ("access".equals(kind)) { claims.claim("role", user.getRole()); }
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT).build(), claims.build());
        try {
            jwt.sign(new MACSigner(key));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Token signing failed", exception);
        }
    }

    private JwtDecoder decoder(byte[] key, String kind) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> claimsValidator = jwt -> {
            boolean valid;
            try {
                UUID.fromString(jwt.getSubject());
                UUID.fromString(jwt.getId());
                valid = kind.equals(jwt.getClaimAsString("token_type"))
                        && jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
                        && jwt.getExpiresAt().isAfter(Instant.now())
                        && !jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(30));
            } catch (RuntimeException exception) {
                valid = false;
            }
            return valid ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), claimsValidator));
        return decoder;
    }

    public record IssuedTokens(String accessToken, String refreshToken, long expiresIn, long refreshExpiresIn) {
        @Override
        public String toString() { return "IssuedTokens[REDACTED]"; }
    }
}
