package com.nutee.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nutee.backend.user.entity.User;
import jakarta.validation.Validation;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtConfigurationTest {
    private static final String ACCESS = "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=";
    private static final String REFRESH = "QkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkI=";
    private HmacTokenService tokens;
    private User user;

    @BeforeEach
    void configure() {
        tokens = new HmacTokenService(new JwtProperties(ACCESS, REFRESH, Duration.ofMinutes(15), Duration.ofDays(7)));
        user = User.create("tester", "test@example.com", "hash", "Test", false, null);
    }

    @Test
    void issuesHs256TokensWithIndependentKeysAndClaims() {
        var issued = tokens.issue(user);
        var access = tokens.decodeAccess(issued.accessToken());
        var refresh = tokens.decodeRefresh(issued.refreshToken());
        assertThat(access.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(access.getSubject()).isEqualTo(user.getId().toString());
        assertThat(access.getClaimAsString("email")).isEqualTo(user.getEmail());
        assertThat(refresh.getClaimAsString("email")).isEqualTo(user.getEmail());
        assertThat(refresh.getSubject()).isEqualTo(user.getId().toString());
        assertThat(access.hasClaim("sid")).isFalse();
        assertThat(access.hasClaim("iss")).isFalse();
        assertThat(access.hasClaim("aud")).isFalse();
        assertThat(access.getClaimAsString("role")).isEqualTo("CUSTOMER");
        assertThat(refresh.getClaimAsString("token_type")).isEqualTo("refresh");
        assertThat(refresh.getId()).isNotEqualTo(access.getId());
        assertThat(issued.expiresIn()).isEqualTo(900);
        assertThat(issued.refreshExpiresIn()).isEqualTo(Duration.ofDays(7).toSeconds());
        assertThatThrownBy(() -> tokens.decodeAccess(issued.refreshToken())).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> tokens.decodeRefresh(issued.accessToken())).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiryTypeAndMissingRequiredClaims() throws Exception {
        for (String invalid : new String[]{
                sign("nutee-backend", "nutee-api", "access", Instant.now().minusSeconds(1), true),
                sign("nutee-backend", "nutee-api", "refresh", Instant.now().plusSeconds(300), true),
                sign("nutee-backend", "nutee-api", "access", Instant.now().plusSeconds(300), false)}) {
            assertThatThrownBy(() -> tokens.decodeAccess(invalid)).isInstanceOf(JwtException.class);
        }
    }

    @Test
    void rejectsWrongSecretAndAlgorithm() throws Exception {
        var issued = tokens.issue(user, Instant.now().plusSeconds(300));
        HmacTokenService other = new HmacTokenService(new JwtProperties(REFRESH, ACCESS, Duration.ofMinutes(15), Duration.ofDays(7)));
        assertThatThrownBy(() -> other.decodeAccess(issued.accessToken())).isInstanceOf(JwtException.class);
        SignedJWT hs384 = new SignedJWT(new JWSHeader(JWSAlgorithm.HS384), new JWTClaimsSet.Builder()
                .subject(user.getId().toString()).build());
        hs384.sign(new MACSigner(new byte[48]));
        assertThatThrownBy(() -> tokens.decodeAccess(hs384.serialize())).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> tokens.decodeAccess("eyJhbGciOiJub25lIn0.e30."))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refusesWeakEqualAndMissingKeys() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new JwtProperties(ACCESS, REFRESH, Duration.ofMinutes(15), Duration.ofDays(7)))).isEmpty();
            assertThat(validator.validate(new JwtProperties(ACCESS, ACCESS, Duration.ofMinutes(15), Duration.ofDays(7)))).isNotEmpty();
            assertThat(validator.validate(new JwtProperties("c2hvcnQ=", REFRESH, Duration.ofMinutes(15), Duration.ofDays(7)))).isNotEmpty();
            assertThat(validator.validate(new JwtProperties(null, null, Duration.ofMinutes(15), Duration.ofDays(7)))).isNotEmpty();
        }
    }

    private String sign(String issuer, String audience, String kind, Instant expiry, boolean identifiers)
            throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience)
                .subject(user.getId().toString()).expirationTime(Date.from(expiry))
                .issueTime(Date.from(Instant.now())).claim("token_type", kind);
        if (identifiers) { claims.jwtID(UUID.randomUUID().toString()); }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jwt.sign(new MACSigner(Base64.getDecoder().decode(ACCESS)));
        return jwt.serialize();
    }
}
