package com.nutee.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutee.backend.auth.service.AuthService;
import com.nutee.backend.auth.service.SocialIdentity;
import com.nutee.backend.auth.service.GoogleTokenVerifier;
import com.nutee.backend.security.HmacTokenService;
import com.nutee.backend.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {"app.security.cors.allowed-origins=http://localhost:3000", "app.password-reset.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthFlowIT {
    private static final String PASSWORD = "StrongPassword!123";
    private static final String NONCE = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwords;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AuthService auth;
    @Autowired private HmacTokenService tokens;
    @MockitoBean private GoogleTokenVerifier google;
    @MockitoBean private com.nutee.backend.auth.service.GmailService mail;
    @Autowired private com.nutee.backend.auth.service.PasswordResetService passwordReset;
    @Autowired private com.nutee.backend.auth.service.EmailVerificationService verification;

    @Test
    void otpFailuresCommitAcrossTransactionsAndCorrectCodeCannotBypassLimit() {
        var user = com.nutee.backend.user.entity.User.create(username(), UUID.randomUUID() + "@example.com",
                passwords.encode(PASSWORD), "OTP Test", true, null);
        users.saveAndFlush(user);
        passwordReset.forgot(user.getEmail());
        var otp = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mail).sendPasswordResetOtp(
                org.mockito.ArgumentMatchers.eq(user.getEmail()), anyString(), otp.capture());
        String wrong = otp.getValue().equals("000000") ? "000001" : "000000";
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThatThrownBy(() -> passwordReset.reset(user.getEmail(), wrong, "ReplacementPassword123!"))
                    .isInstanceOf(com.nutee.backend.common.exception.ApiException.class);
            assertThat(users.findById(user.getId()).orElseThrow().getPasswordResetAttempts()).isEqualTo(attempt);
        }
        assertThatThrownBy(() -> passwordReset.reset(user.getEmail(), otp.getValue(), "ReplacementPassword123!"))
                .isInstanceOf(com.nutee.backend.common.exception.ApiException.class);
        assertThat(users.findById(user.getId()).orElseThrow().getPasswordResetHash()).isNull();
    }

    @BeforeEach
    void configureGoogleStrategy() {
        given(google.name()).willReturn("GOOGLE");
    }

    @Test
    void registrationHashesPasswordAndReturnsOnlyPendingVerification() throws Exception {
        String username = username();
        JsonNode response = registerPending(username);
        var user = users.findByUsernameIgnoreCase(username).orElseThrow();
        assertThat(passwords.matches(PASSWORD, user.getPassword())).isTrue();
        assertThat(user.getPassword()).isNotEqualTo(PASSWORD);
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(response.path("user").has("password")).isFalse();
        assertThat(response.path("user").has("refreshToken")).isFalse();
        assertThat(response.path("user").isNull()).isTrue();
        assertThat(response.path("verificationRequired").asBoolean()).isTrue();
        assertThat(response.path("verification").path("email").asText()).isEqualTo(user.getEmail());
        assertThat(response.path("expiresIn").asLong()).isZero();
        String stored = jdbc.queryForObject("SELECT refresh_token FROM users WHERE id = ?",
                String.class, user.getId());
        assertThat(stored).isNull();
    }

    @Test
    void registrationAcceptsFrontendEmailAndPasswordWithoutUsername() throws Exception {
        String email = username() + "@example.com";
        mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verificationRequired").value(true))
                .andExpect(jsonPath("$.verification.email").value(email));
    }

    @Test
    void loginAcceptsEmailWithoutCaseSensitivityAndRejectsUsername() throws Exception {
        String username = username();
        register(username);
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("identifier", username, "password", PASSWORD))))
                .andExpect(status().isBadRequest());
        for (String identifier : new String[]{(username + "@example.com").toUpperCase(), " " + username + "@example.com "}) {
            mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(Map.of("identifier", identifier, "password", PASSWORD))))
                    .andExpect(status().isOk()).andExpect(header().exists("Set-Cookie"));
        }
    }

    @Test
    void newLoginReplacesRefreshTokenAndOldLogoutCannotClearIt() throws Exception {
        String username = username();
        JsonNode original = register(username);
        var result = mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("identifier", username + "@example.com", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse();
        JsonNode latest = cookieSession(result);
        refresh(original.path("refreshToken").asText(), 401);
        mvc.perform(post("/api/v1/auth/logout").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .cookie(new Cookie("refreshToken", original.path("refreshToken").asText())))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT refresh_token FROM users WHERE username = ?", String.class,
                username)).isEqualTo(latest.path("refreshToken").asText());
        refresh(latest.path("refreshToken").asText(), 200);
    }

    @Test
    void wrongPasswordAndUnknownUserReturnSameUnauthorizedShape() throws Exception {
        String username = username();
        register(username);
        for (String identifier : new String[]{username + "@example.com", "unknown_" + username + "@example.com"}) {
            mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(Map.of("identifier", identifier, "password", "wrong-password"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Email hoặc mật khẩu không đúng."));
        }
    }

    @Test
    void duplicatesAreRejectedCaseInsensitively() throws Exception {
        String username = username();
        register(username);
        for (Map<String, String> request : java.util.List.of(
                Map.of("username", username.toUpperCase(), "email", username + "other@example.com", "password", PASSWORD),
                Map.of("username", username + "other", "email", (username + "@example.com").toUpperCase(), "password", PASSWORD))) {
            mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(request))).andExpect(status().isConflict());
        }
    }

    @Test
    void validationRejectsWeakAndOverlongUtf8Passwords() throws Exception {
        for (String password : new String[]{"short", "é".repeat(40)}) {
            String username = username();
            mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(Map.of("username", username, "email", username + "@example.com",
                            "password", password)))).andExpect(status().isBadRequest());
        }
    }

    @Test
    void accessAndRefreshTokensCannotBeSubstituted() throws Exception {
        JsonNode response = register(username());
        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("accessToken", response.path("accessToken").asText())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(response.path("user").path("id").asText()));
        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("accessToken", response.path("refreshToken").asText())))
                .andExpect(status().isUnauthorized());
        refresh(response.path("accessToken").asText(), 401);
        refresh("invalid-token", 401);
    }

    @Test
    void rotationRejectsOldTokenAndKeepsCurrentTokenUsable() throws Exception {
        JsonNode original = register(username());
        JsonNode rotated = refresh(original.path("refreshToken").asText(), 200);
        assertThat(rotated.path("refreshToken").asText()).isNotEqualTo(original.path("refreshToken").asText());
        refresh(original.path("refreshToken").asText(), 401);
        assertThat(tokens.decodeRefresh(rotated.path("refreshToken").asText()).getExpiresAt())
                .isEqualTo(tokens.decodeRefresh(original.path("refreshToken").asText()).getExpiresAt());
        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("accessToken", rotated.path("accessToken").asText())))
                .andExpect(status().isOk());
        refresh(rotated.path("refreshToken").asText(), 200);
    }

    @Test
    void logoutClearsRefreshTokenAndAccessRemainsValidUntilExpiry() throws Exception {
        JsonNode response = register(username());
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/auth/logout").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .cookie(new Cookie("refreshToken", response.path("refreshToken").asText())))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("accessToken", response.path("accessToken").asText())))
                .andExpect(status().isOk());
        refresh(response.path("refreshToken").asText(), 401);
        assertThat(jdbc.queryForObject("SELECT refresh_token FROM users WHERE id = ?", String.class,
                UUID.fromString(response.path("user").path("id").asText()))).isNull();
    }

    @Test
    void disabledAccountsCannotLoginRefreshOrUseExistingAccess() throws Exception {
        String username = username();
        JsonNode response = register(username);
        jdbc.update("UPDATE users SET is_active = false WHERE username = ?", username);
        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("accessToken", response.path("accessToken").asText())))
                .andExpect(status().isUnauthorized());
        refresh(response.path("refreshToken").asText(), 401);
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("identifier", username + "@example.com", "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredRefreshTokenIsRejectedEvenWhenStoredOnUser() throws Exception {
        JsonNode response = register(username());
        UUID id = UUID.fromString(response.path("user").path("id").asText());
        var user = users.findById(id).orElseThrow();
        String expired = tokens.issue(user, Instant.now().minusSeconds(60)).refreshToken();
        jdbc.update("UPDATE users SET refresh_token = ? WHERE id = ?", expired, id);
        refresh(expired, 401);
    }

    @Test
    void concurrentRefreshOnlyAcceptsTheStoredTokenOnce() throws Exception {
        JsonNode response = register(username());
        String refreshToken = response.path("refreshToken").asText();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<Boolean>) () -> {
                start.await();
                try { auth.refresh(refreshToken); return true; }
                catch (BadCredentialsException exception) { return false; }
            };
            var first = pool.submit(task);
            var second = pool.submit(task);
            start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThatThrownBy(() -> auth.refresh(refreshToken)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void googleCreatesUserAndSubsequentLoginFindsSameProviderIdentity() throws Exception {
        String email = username() + "@example.com";
        given(google.verify(anyString(), anyString())).willReturn(new SocialIdentity(UUID.randomUUID().toString(),
                email, "Google User", "https://example.com/photo"));
        JsonNode first = googleLogin(200);
        JsonNode second = googleLogin(200);
        assertThat(second.path("user").path("id")).isEqualTo(first.path("user").path("id"));
        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getPassword()).isNull();
        assertThat(user.isEmailVerified()).isTrue();
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("identifier", email, "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleDoesNotAutoLinkExistingEmailButAllowsAuthenticatedLinking() throws Exception {
        String username = username();
        JsonNode local = register(username);
        given(google.verify(anyString(), anyString())).willReturn(new SocialIdentity(UUID.randomUUID().toString(),
                "other@example.invalid", "Other Google User", null));
        mvc.perform(post("/api/v1/auth/social/google/link").with(csrf())
                .cookie(new Cookie("nutee_google_nonce", NONCE))
                .cookie(new Cookie("accessToken", local.path("accessToken").asText()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"credential\":\"google-token\"}"))
                .andExpect(status().isConflict());
        given(google.verify(anyString(), anyString())).willReturn(new SocialIdentity(UUID.randomUUID().toString(),
                username + "@example.com", "Google User", null));
        googleLogin(409);
        mvc.perform(post("/api/v1/auth/social/google/link").with(csrf()).cookie(new Cookie("nutee_google_nonce", NONCE))
                .cookie(new Cookie("accessToken", local.path("accessToken").asText()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"credential\":\"google-token\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
        JsonNode linked = googleLogin(200);
        assertThat(linked.path("user").path("id")).isEqualTo(local.path("user").path("id"));
    }

    @Test
    void googleRequiresNonceAndRejectsInvalidCredential() throws Exception {
        mvc.perform(post("/api/v1/auth/google").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"google-token\"}")).andExpect(status().isUnauthorized());
        given(google.verify(anyString(), anyString())).willThrow(new BadCredentialsException("Invalid Google token"));
        googleLogin(401);
        var nonceResponse = mvc.perform(get("/api/v1/auth/google/nonce")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        Cookie cookie = nonceResponse.getCookie("nutee_google_nonce");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getMaxAge()).isEqualTo(300);
        assertThat(cookie.getValue()).hasSize(43);
    }

    @Test
    void corsAllowsOnlyExplicitFrontendOrigin() throws Exception {
        mvc.perform(options("/api/v1/auth/login").header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mvc.perform(options("/api/v1/auth/login").header("Origin", "https://attacker.example")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void pendingCookieIsRequiredAndCannotReplaceEitherSessionToken() throws Exception {
        var pending = registerPending(username());
        String token = pending.path("emailVerification").asText();
        mvc.perform(get("/api/v1/auth/verification").cookie(new Cookie("emailVerification", token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resendAfter").value(org.hamcrest.Matchers.greaterThan(0)));
        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("accessToken", token))).andExpect(status().isUnauthorized());
        refresh(token, 401);
        mvc.perform(post("/api/v1/auth/verification/confirm").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"otp\":\"123456\",\"email\":\"test@example.com\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(new Cookie("emailVerification", token)))
                .andExpect(status().isNoContent()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie().maxAge("emailVerification", 0));
    }

    @Test
    void unverifiedAccountsOfEveryRoleCannotUseLegacyAccessAndRefreshTokens() throws Exception {
        for (String role : new String[] {"CUSTOMER", "STAFF", "ADMIN"}) {
            JsonNode session = register(username());
            UUID id = UUID.fromString(session.path("user").path("id").asText());
            jdbc.update("UPDATE users SET role = ?, email_verified = false WHERE id = ?", role, id);
            var response = mvc.perform(get("/api/v1/auth/me")
                    .cookie(new Cookie("accessToken", session.path("accessToken").asText())))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie().maxAge("accessToken", 0))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie().httpOnly("emailVerification", true))
                    .andReturn().getResponse();
            assertThat(users.findById(id).orElseThrow().getRefreshToken()).isNull();
            assertThat(users.findById(id).orElseThrow().isEmailVerified()).isFalse();
            mvc.perform(get("/api/v1/cart").cookie(new Cookie("accessToken", session.path("accessToken").asText())))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
            mvc.perform(get("/api/v1/management/dashboard").cookie(new Cookie("accessToken", session.path("accessToken").asText())))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
            mvc.perform(get("/api/v1/products").cookie(new Cookie("accessToken", session.path("accessToken").asText())))
                    .andExpect(status().isOk());
            assertThat(response.getCookie("emailVerification").getMaxAge()).isBetween(1, 900);
        }
    }

    @Test
    void refreshOfUnverifiedAccountCommitsRevocationAndClearsLoginCookies() throws Exception {
        JsonNode session = register(username());
        UUID id = UUID.fromString(session.path("user").path("id").asText());
        jdbc.update("UPDATE users SET email_verified = false WHERE id = ?", id);
        mvc.perform(post("/api/v1/auth/refresh").with(csrf())
                .cookie(new Cookie("refreshToken", session.path("refreshToken").asText())))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie().maxAge("accessToken", 0))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie().maxAge("refreshToken", 0));
        assertThat(users.findById(id).orElseThrow().getRefreshToken()).isNull();
    }

    @Test
    void verificationFailuresCommitAndResendResetsAttemptsWithoutAffectingPasswordReset() throws Exception {
        String name = username();
        JsonNode pending = registerPending(name);
        String token = pending.path("emailVerification").asText();
        var user = users.findByUsernameIgnoreCase(name).orElseThrow();
        var otp = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mail).sendEmailVerificationOtp(org.mockito.ArgumentMatchers.eq(user.getEmail()), otp.capture());
        passwordReset.forgot(user.getEmail());
        String resetHash = users.findById(user.getId()).orElseThrow().getPasswordResetHash();
        String wrong = otp.getValue().equals("000000") ? "000001" : "000000";
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThatThrownBy(() -> verification.confirm(token, wrong)).isInstanceOf(com.nutee.backend.common.exception.ApiException.class);
            assertThat(users.findById(user.getId()).orElseThrow().getEmailVerificationAttempts()).isEqualTo(attempt);
        }
        assertThatThrownBy(() -> verification.confirm(token, otp.getValue())).isInstanceOf(com.nutee.backend.common.exception.ApiException.class);
        assertThatThrownBy(() -> verification.resend(token)).isInstanceOf(com.nutee.backend.common.exception.ApiException.class);
        jdbc.update("UPDATE users SET email_verification_expires_at = now() + interval '800 seconds' WHERE id = ?", user.getId());
        verification.resend(token);
        var updated = users.findById(user.getId()).orElseThrow();
        assertThat(updated.getEmailVerificationAttempts()).isZero();
        assertThat(updated.getEmailVerificationHash()).isNotNull();
        assertThat(updated.getPasswordResetHash()).isEqualTo(resetHash);
        assertThat(updated.isEmailVerified()).isFalse();
    }

    @Test
    void concurrentConfirmationConsumesTheOtpExactlyOnce() throws Exception {
        String name = username();
        JsonNode pending = registerPending(name);
        var otp = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mail).sendEmailVerificationOtp(org.mockito.ArgumentMatchers.eq(name + "@example.com"), otp.capture());
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<Boolean>) () -> {
                start.await();
                try { verification.confirm(pending.path("emailVerification").asText(), otp.getValue()); return true; }
                catch (BadCredentialsException exception) { return false; }
            };
            var first = pool.submit(task);
            var second = pool.submit(task);
            start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        var verified = users.findByUsernameIgnoreCase(name).orElseThrow();
        assertThat(verified.isEmailVerified()).isTrue();
        assertThat(verified.getEmailVerificationHash()).isNull();
    }

    @Test
    void concurrentRegistrationKeepsOneAccountAndNeverOverwritesCredentials() throws Exception {
        String email = username() + "@example.com";
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<Integer>) () -> {
                start.await();
                return mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("email", email.toUpperCase(), "password", PASSWORD))))
                        .andReturn().getResponse().getStatus();
            };
            var first = pool.submit(task);
            var second = pool.submit(task);
            start.countDown();
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE lower(email) = ?", Integer.class, email)).isEqualTo(1);
        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(passwords.matches(PASSWORD, user.getPassword())).isTrue();
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void verificationMigrationPreservesExistingAccountsAndPasswordResetState() {
        String schema = "verification_upgrade";
        org.flywaydb.core.Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).target("6").load().migrate();
        jdbc.execute("INSERT INTO verification_upgrade.users (id, username, email, email_verified, password_reset_hash, password_reset_attempts) VALUES "
                + "('10000000-0000-0000-0000-000000000001', 'legacy', 'legacy@example.invalid', false, 'reset-hash', 2), "
                + "('10000000-0000-0000-0000-000000000002', 'verified', 'verified@example.invalid', true, NULL, 0)");
        var upgrade = org.flywaydb.core.Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema).load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT email_verified FROM verification_upgrade.users ORDER BY username", Boolean.class))
                .containsExactly(false, true);
        assertThat(jdbc.queryForObject("SELECT password_reset_hash FROM verification_upgrade.users WHERE username = 'legacy'", String.class)).isEqualTo("reset-hash");
        assertThat(jdbc.queryForObject("SELECT password_reset_attempts FROM verification_upgrade.users WHERE username = 'legacy'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT email_verification_attempts FROM verification_upgrade.users", Integer.class)).containsOnly(0);
        upgrade.validate();
        assertThat(upgrade.migrate().migrationsExecuted).isZero();
    }

    @Test
    void failedRegistrationMailPreservesPendingAccountAndCanBeRetried() throws Exception {
        String name = username();
        org.mockito.Mockito.doThrow(new com.nutee.backend.auth.service.GmailService.DeliveryException())
                .when(mail).sendEmailVerificationOtp(org.mockito.ArgumentMatchers.eq(name + "@example.com"), anyString());
        JsonNode pending = registerPending(name);
        assertThat(pending.path("verification").path("mailSent").asBoolean()).isFalse();
        var user = users.findByUsernameIgnoreCase(name).orElseThrow();
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getRefreshToken()).isNull();
        org.mockito.Mockito.doNothing().when(mail).sendEmailVerificationOtp(org.mockito.ArgumentMatchers.eq(user.getEmail()), anyString());
        assertThat(verification.resend(pending.path("emailVerification").asText()).mailSent()).isTrue();
    }

    @Test
    void concurrentResendsSendOnlyOneNewCode() throws Exception {
        String name = username();
        JsonNode pending = registerPending(name);
        var user = users.findByUsernameIgnoreCase(name).orElseThrow();
        jdbc.update("UPDATE users SET email_verification_expires_at = now() + interval '800 seconds' WHERE id = ?", user.getId());
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<Integer>) () -> {
                start.await();
                try { verification.resend(pending.path("emailVerification").asText()); return 200; }
                catch (com.nutee.backend.common.exception.ApiException exception) { return exception.getStatus().value(); }
            };
            var first = pool.submit(task);
            var second = pool.submit(task);
            start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 429);
        }
        org.mockito.Mockito.verify(mail, org.mockito.Mockito.times(2)).sendEmailVerificationOtp(
                org.mockito.ArgumentMatchers.eq(user.getEmail()), anyString());
    }

    @Test
    void passwordResetDoesNotVerifyEmailAndNeitherOtpWorksForTheOtherPurpose() throws Exception {
        String name = username();
        JsonNode pending = registerPending(name);
        String token = pending.path("emailVerification").asText();
        var user = users.findByUsernameIgnoreCase(name).orElseThrow();
        var emailOtp = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mail).sendEmailVerificationOtp(org.mockito.ArgumentMatchers.eq(user.getEmail()), emailOtp.capture());
        passwordReset.forgot(user.getEmail());
        var resetOtp = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mail).sendPasswordResetOtp(org.mockito.ArgumentMatchers.eq(user.getEmail()), anyString(), resetOtp.capture());
        // Hai mã ngẫu nhiên có thể trùng sáu chữ số; kiểm tra tách mục đích khi giá trị khác nhau.
        if (!emailOtp.getValue().equals(resetOtp.getValue())) {
            assertThatThrownBy(() -> verification.confirm(token, resetOtp.getValue())).isInstanceOf(com.nutee.backend.common.exception.ApiException.class);
            assertThatThrownBy(() -> passwordReset.reset(user.getEmail(), emailOtp.getValue(), "ReplacementPassword123!"))
                    .isInstanceOf(com.nutee.backend.common.exception.ApiException.class);
        }
        passwordReset.reset(user.getEmail(), resetOtp.getValue(), "ReplacementPassword123!");
        assertThat(users.findById(user.getId()).orElseThrow().isEmailVerified()).isFalse();
        var login = auth.login(new com.nutee.backend.auth.dto.LoginRequest(user.getEmail(), "ReplacementPassword123!"));
        assertThat(login.verificationRequired()).isTrue();
        assertThat(login.accessToken()).isNull();
    }

    private JsonNode register(String username) throws Exception {
        JsonNode pending = registerPending(username);
        var otp = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mail).sendEmailVerificationOtp(
                org.mockito.ArgumentMatchers.eq(username + "@example.com"), otp.capture());
        var response = mvc.perform(post("/api/v1/auth/verification/confirm").with(csrf())
                .cookie(new Cookie("emailVerification", pending.path("emailVerification").asText()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("otp", otp.getValue()))))
                .andExpect(status().isOk()).andReturn().getResponse();
        return cookieSession(response);
    }

    private JsonNode registerPending(String username) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("username", username, "email", username + "@example.com",
                        "password", PASSWORD, "role", "ADMIN"))))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();
        return cookieSession(response);
    }

    private JsonNode refresh(String token, int expected) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/refresh").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .cookie(new Cookie("refreshToken", token)))
                .andExpect(status().is(expected)).andReturn().getResponse();
        return cookieSession(response);
    }

    private JsonNode googleLogin(int expected) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/google").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .cookie(new Cookie("nutee_google_nonce", NONCE)).content("{\"idToken\":\"google-token\"}"))
                .andExpect(status().is(expected)).andReturn().getResponse();
        return cookieSession(response);
    }

    // Tokens are extracted only from HttpOnly Set-Cookie for server-side integration assertions.
    private JsonNode cookieSession(org.springframework.mock.web.MockHttpServletResponse response) throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(response.getContentAsString());
        assertThat(body.has("accessToken")).isFalse();
        assertThat(body.has("refreshToken")).isFalse();
        assertThat(body.has("verificationToken")).isFalse();
        for (String name : new String[]{"accessToken", "refreshToken", "emailVerification"}) {
            Cookie cookie = response.getCookie(name);
            if (cookie != null && !cookie.getValue().isBlank()) {
                assertThat(cookie.isHttpOnly()).isTrue();
                body.put(name, cookie.getValue());
            }
        }
        return body;
    }

    private static String username() { return "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
}
