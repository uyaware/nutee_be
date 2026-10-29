package com.nutee.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.security.GoogleProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

class GoogleTokenVerifierTest {
    private RSAKey key;
    private HttpServer server;
    private GoogleTokenVerifier verifier;

    @BeforeEach
    void configureTrustedTestJwkSet() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        key = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate()).keyID("google-test").build();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        byte[] json = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        server.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.length);
            try (var output = exchange.getResponseBody()) { output.write(json); }
        });
        server.start();
        verifier = new GoogleTokenVerifier(new GoogleProperties(true, "test-client"),
                "http://localhost:" + server.getAddress().getPort() + "/jwks");
    }

    @AfterEach
    void stopServer() { if (server != null) { server.stop(0); } }

    @Test
    void verifiesSignatureAndGoogleClaimsAndNonce() throws Exception {
        var identity = verifier.verify(token("https://accounts.google.com", "test-client", true,
                "nonce", Instant.now().plusSeconds(300)), "nonce");
        assertThat(identity.subject()).isEqualTo("google-subject");
        assertThat(identity.email()).isEqualTo("google@example.com");
    }

    @Test
    void rejectsWrongIssuerAudienceEmailVerificationExpiryAndNonce() throws Exception {
        String good = token("https://accounts.google.com", "test-client", true, "nonce", Instant.now().plusSeconds(300));
        assertThatThrownBy(() -> verifier.verify(good, "other-nonce")).isInstanceOf(BadCredentialsException.class);
        for (String invalid : new String[]{
                token("https://attacker.example", "test-client", true, "nonce", Instant.now().plusSeconds(300)),
                token("https://accounts.google.com", "other-client", true, "nonce", Instant.now().plusSeconds(300)),
                token("https://accounts.google.com", "test-client", false, "nonce", Instant.now().plusSeconds(300)),
                token("https://accounts.google.com", "test-client", true, "nonce", Instant.now().minusSeconds(1))}) {
            assertThatThrownBy(() -> verifier.verify(invalid, "nonce")).isInstanceOf(BadCredentialsException.class);
        }
    }

    @Test
    void disabledGoogleRequiresConfiguration() {
        GoogleTokenVerifier disabled = new GoogleTokenVerifier(new GoogleProperties(false, ""));
        assertThatThrownBy(() -> disabled.verify("anything", "nonce")).isInstanceOf(ApiException.class);
    }

    private String token(String issuer, String audience, boolean verified, String nonce, Instant expiry)
            throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject("google-subject")
                .issueTime(new Date()).expirationTime(Date.from(expiry)).claim("email", "google@example.com")
                .claim("email_verified", verified).claim("nonce", nonce).claim("name", "Google User").build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("google-test").build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
