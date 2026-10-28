package com.nutee.backend;

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
import com.nutee.backend.auth.service.GoogleIdentity;
import com.nutee.backend.auth.service.GoogleTokenVerifier;
import com.nutee.backend.auth.service.InvalidRefreshTokenException;
import com.nutee.backend.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
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

@SpringBootTest(properties = "app.security.cors.allowed-origins=http://localhost:3000")
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
    @MockitoBean private GoogleTokenVerifier google;

    @Test
    void registrationHashesPasswordAndReturnsSafeTokensAndUser() throws Exception {
        String username = username();
        JsonNode response = register(username);
        var user = users.findByUsernameIgnoreCase(username).orElseThrow();
        assertThat(passwords.matches(PASSWORD, user.getPassword())).isTrue();
        assertThat(user.getPassword()).isNotEqualTo(PASSWORD);
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(response.path("user").has("password")).isFalse();
        assertThat(response.path("user").path("role").asText()).isEqualTo("CUSTOMER");
        assertThat(response.path("expiresIn").asLong()).isEqualTo(900);
        String stored = jdbc.queryForObject("SELECT token_hash FROM refresh_sessions WHERE user_id = ?",
                String.class, user.getId());
        assertThat(stored).hasSize(64).isNotEqualTo(response.path("refreshToken").asText());
    }

    @Test
    void loginAcceptsEmailOrUsernameWithoutCaseSensitivity() throws Exception {
        String username = username();
        register(username);
        for (String identifier : new String[]{username.toUpperCase(), (username + "@example.com").toUpperCase()}) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(Map.of("identifier", identifier, "password", PASSWORD))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        }
    }

    @Test
    void wrongPasswordAndUnknownUserReturnSameUnauthorizedShape() throws Exception {
        String username = username();
        register(username);
        for (String identifier : new String[]{username, "unknown_" + username}) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(Map.of("identifier", identifier, "password", "wrong-password"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Authentication is required."));
        }
    }

    @Test
    void duplicatesAreRejectedCaseInsensitively() throws Exception {
        String username = username();
        register(username);
        for (Map<String, String> request : java.util.List.of(
                Map.of("username", username.toUpperCase(), "email", username + "other@example.com", "password", PASSWORD),
                Map.of("username", username + "other", "email", (username + "@example.com").toUpperCase(), "password", PASSWORD))) {
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(request))).andExpect(status().isConflict());
        }
    }

    @Test
    void validationRejectsWeakAndOverlongUtf8Passwords() throws Exception {
        for (String password : new String[]{"short", "é".repeat(40)}) {
            String username = username();
            mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(Map.of("username", username, "email", username + "@example.com",
                            "password", password)))).andExpect(status().isBadRequest());
        }
    }

    @Test
    void accessAndRefreshTokensCannotBeSubstituted() throws Exception {
        JsonNode response = register(username());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + response.path("accessToken").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(response.path("user").path("id").asText()));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + response.path("refreshToken").asText()))
                .andExpect(status().isUnauthorized());
        refresh(response.path("accessToken").asText(), 401);
        refresh("invalid-token", 401);
    }

    @Test
    void rotationAndReplayDetectionCommitRevocation() throws Exception {
        JsonNode original = register(username());
        JsonNode rotated = refresh(original.path("refreshToken").asText(), 200);
        assertThat(rotated.path("refreshToken").asText()).isNotEqualTo(original.path("refreshToken").asText());
        refresh(original.path("refreshToken").asText(), 401);
        refresh(rotated.path("refreshToken").asText(), 401);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + rotated.path("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM refresh_sessions WHERE user_id = ?",
                Boolean.class, UUID.fromString(original.path("user").path("id").asText()))).isTrue();
    }

    @Test
    void logoutImmediatelyRevokesAccessAndRefreshForThatSession() throws Exception {
        JsonNode response = register(username());
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(Map.of("refreshToken", response.path("refreshToken").asText()))))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + response.path("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        refresh(response.path("refreshToken").asText(), 401);
    }

    @Test
    void disabledAccountsCannotLoginRefreshOrUseExistingAccess() throws Exception {
        String username = username();
        JsonNode response = register(username);
        jdbc.update("UPDATE users SET is_active = false WHERE username = ?", username);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + response.path("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        refresh(response.path("refreshToken").asText(), 401);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("identifier", username, "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshSessionHasAbsoluteExpiry() throws Exception {
        JsonNode response = register(username());
        jdbc.update("UPDATE refresh_sessions SET expires_at = now() - interval '1 second' WHERE user_id = ?",
                UUID.fromString(response.path("user").path("id").asText()));
        refresh(response.path("refreshToken").asText(), 401);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + response.path("accessToken").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRefreshSerializesAndDetectsReplay() throws Exception {
        JsonNode response = register(username());
        String refreshToken = response.path("refreshToken").asText();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<Boolean>) () -> {
                start.await();
                try { auth.refresh(refreshToken); return true; }
                catch (InvalidRefreshTokenException exception) { return false; }
            };
            var first = pool.submit(task);
            var second = pool.submit(task);
            start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThatThrownBy(() -> auth.refresh(refreshToken)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void googleCreatesUserAndSubsequentLoginFindsSameProviderIdentity() throws Exception {
        String email = username() + "@example.com";
        given(google.verify(anyString(), anyString())).willReturn(new GoogleIdentity(UUID.randomUUID().toString(),
                email, "Google User", "https://example.com/photo"));
        JsonNode first = googleLogin(200);
        JsonNode second = googleLogin(200);
        assertThat(second.path("user").path("id")).isEqualTo(first.path("user").path("id"));
        var user = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getPassword()).isNull();
        assertThat(user.isEmailVerified()).isTrue();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("identifier", email, "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void googleDoesNotAutoLinkExistingEmailButAllowsAuthenticatedLinking() throws Exception {
        String username = username();
        JsonNode local = register(username);
        given(google.verify(anyString(), anyString())).willReturn(new GoogleIdentity(UUID.randomUUID().toString(),
                username + "@example.com", "Google User", null));
        googleLogin(409);
        mvc.perform(post("/api/v1/auth/google/link").cookie(new Cookie("nutee_google_nonce", NONCE))
                .header("Authorization", "Bearer " + local.path("accessToken").asText())
                .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"google-token\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.emailVerified").value(true));
        JsonNode linked = googleLogin(200);
        assertThat(linked.path("user").path("id")).isEqualTo(local.path("user").path("id"));
    }

    @Test
    void googleRequiresNonceAndRejectsInvalidCredential() throws Exception {
        mvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON)
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
    void schemaRejectsMismatchedProductAndSku() {
        int product = jdbc.queryForObject("INSERT INTO products (name) VALUES ('first') RETURNING id", Integer.class);
        int other = jdbc.queryForObject("INSERT INTO products (name) VALUES ('second') RETURNING id", Integer.class);
        String sku = UUID.randomUUID().toString().substring(0, 16);
        jdbc.update("INSERT INTO product_variants (product_id, sku) VALUES (?, ?)", product, sku);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO reviews (product_id, sku) VALUES (?, ?)", other, sku))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private JsonNode register(String username) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("username", username, "email", username + "@example.com",
                        "password", PASSWORD, "role", "ADMIN"))))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();
        return json.readTree(response.getContentAsString());
    }

    private JsonNode refresh(String token, int expected) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("refreshToken", token))))
                .andExpect(status().is(expected)).andReturn().getResponse();
        return json.readTree(response.getContentAsString());
    }

    private JsonNode googleLogin(int expected) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON)
                .cookie(new Cookie("nutee_google_nonce", NONCE)).content("{\"idToken\":\"google-token\"}"))
                .andExpect(status().is(expected)).andReturn().getResponse();
        return json.readTree(response.getContentAsString());
    }

    private static String username() { return "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
}
