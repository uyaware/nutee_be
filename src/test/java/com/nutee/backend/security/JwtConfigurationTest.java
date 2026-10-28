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
        tokens = new HmacTokenService(new JwtProperties("nutee-backend", "nutee-api", ACCESS, REFRESH),
                new TokenProperties(Duration.ofMinutes(15), Duration.ofDays(7)));
        user = User.create("tester", "test@example.com", "hash", "Test", false, null);
    }

    @Test
    void issuesHs256TokensWithIndependentKeysAndClaims() {
        UUID session = UUID.randomUUID();
        var issued = tokens.issue(user, session, Instant.now().plus(Duration.ofDays(7)));
        var access = tokens.decodeAccess(issued.accessToken());
        var refresh = tokens.decodeRefresh(issued.refreshToken());
        assertThat(access.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(access.getSubject()).isEqualTo(user.getId().toString());
        assertThat(access.getClaimAsString("sid")).isEqualTo(session.toString());
        assertThat(access.getClaimAsString("role")).isEqualTo("CUSTOMER");
        assertThat(refresh.getClaimAsString("token_type")).isEqualTo("refresh");
        assertThat(refresh.getId()).isNotEqualTo(access.getId());
        assertThat(issued.expiresIn()).isEqualTo(900);
        assertThat(HmacTokenService.hash(issued.refreshToken())).hasSize(64);
        assertThatThrownBy(() -> tokens.decodeAccess(issued.refreshToken())).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> tokens.decodeRefresh(issued.accessToken())).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWrongIssuerAudienceExpiryTypeAndMissingRequiredClaims() throws Exception {
        for (String invalid : new String[]{
                sign("other", "nutee-api", "access", Instant.now().plusSeconds(300), true),
                sign("nutee-backend", "other-api", "access", Instant.now().plusSeconds(300), true),
                sign("nutee-backend", "nutee-api", "access", Instant.now().minusSeconds(1), true),
                sign("nutee-backend", "nutee-api", "refresh", Instant.now().plusSeconds(300), true),
                sign("nutee-backend", "nutee-api", "access", Instant.now().plusSeconds(300), false)}) {
            assertThatThrownBy(() -> tokens.decodeAccess(invalid)).isInstanceOf(JwtException.class);
        }
    }

    @Test
    void rejectsWrongSecretAndAlgorithm() throws Exception {
        var issued = tokens.issue(user, UUID.randomUUID(), Instant.now().plusSeconds(300));
        HmacTokenService other = new HmacTokenService(new JwtProperties("nutee-backend", "nutee-api", REFRESH, ACCESS),
                new TokenProperties(Duration.ofMinutes(15), Duration.ofDays(7)));
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
            assertThat(validator.validate(new JwtProperties("issuer", "audience", ACCESS, REFRESH))).isEmpty();
            assertThat(validator.validate(new JwtProperties("issuer", "audience", ACCESS, ACCESS))).isNotEmpty();
            assertThat(validator.validate(new JwtProperties("issuer", "audience", "c2hvcnQ=", REFRESH))).isNotEmpty();
            assertThat(validator.validate(new JwtProperties("issuer", "audience", null, null))).isNotEmpty();
        }
    }

    private String sign(String issuer, String audience, String kind, Instant expiry, boolean identifiers)
            throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience)
                .subject(user.getId().toString()).expirationTime(Date.from(expiry))
                .issueTime(Date.from(Instant.now())).claim("token_type", kind);
        if (identifiers) { claims.jwtID(UUID.randomUUID().toString()).claim("sid", UUID.randomUUID().toString()); }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jwt.sign(new MACSigner(Base64.getDecoder().decode(ACCESS)));
        return jwt.serialize();
    }
}
