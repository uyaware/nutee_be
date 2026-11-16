package com.nutee.backend;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.nutee.backend.security.SecurityConfiguration;
import com.nutee.backend.security.SecurityErrorHandler;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.context.ActiveProfiles;

@WebMvcTest(controllers = JwtSecurityWebTest.ProbeController.class)
@Import({SecurityConfiguration.class, SecurityErrorHandler.class, JwtSecurityWebTest.ProbeController.class})
@ActiveProfiles("test")
class JwtSecurityWebTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.nutee.backend.user.repository.UserRepository accountUsers;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.nutee.backend.auth.service.EmailVerificationService emailVerification;


    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder decoder;

    @Test
    void verifiedAccessCookieAuthenticatesWithoutSession() throws Exception {
        var account = com.nutee.backend.user.entity.User.create("tester", "test@example.com", "hash", "Test", true, null);
        given(accountUsers.findById(account.getId())).willReturn(java.util.Optional.of(account));
        given(decoder.decode("verified-token")).willReturn(Jwt.withTokenValue("verified-token")
                .header("alg", "HS256").subject(account.getId().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build());
        mvc.perform(get("/api/v1/_test/jwt").cookie(new Cookie("accessToken", "verified-token")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void invalidAccessCookieGetsConsistentUnauthorizedResponse() throws Exception {
        given(decoder.decode(anyString())).willThrow(new BadJwtException("private-decoder-detail"));
        mvc.perform(get("/api/v1/_test/jwt").cookie(new Cookie("accessToken", "invalid-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.detail").value("Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại."));
    }

    @Test
    void databaseVerificationOverridesMissingAndTrueJwtClaimsForEveryRole() throws Exception {
        for (String role : new String[] {"CUSTOMER", "STAFF", "ADMIN"}) {
            var account = com.nutee.backend.user.entity.User.create("tester", "test@example.com", "hash", "Test", false, null);
            account.updateAccess(role, true);
            given(accountUsers.findById(account.getId())).willReturn(java.util.Optional.of(account));
            given(emailVerification.suspendAccess(account.getId())).willReturn(com.nutee.backend.auth.dto.AuthResponse.pending(
                    "pending-only", new com.nutee.backend.auth.dto.VerificationResponse(account.getEmail(), 900, 0, false, "Verify email")));
            for (boolean legacy : new boolean[] {true, false}) {
                var builder = Jwt.withTokenValue("old-token").header("alg", "HS256").subject(account.getId().toString()).claim("role", role);
                if (!legacy) builder.claim("email_verified", true);
                given(decoder.decode("old-token")).willReturn(builder.build());
                mvc.perform(get("/api/v1/_test/jwt").cookie(new Cookie("accessToken", "old-token")))
                        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie().maxAge("accessToken", 0))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie().httpOnly("emailVerification", true));
            }
        }
    }

    @Test
    void roleComesFromCurrentDatabaseAccountAndNeverFromStaleJwt() throws Exception {
        var account = com.nutee.backend.user.entity.User.create("tester", "test@example.com", "hash", "Test", true, null);
        given(accountUsers.findById(account.getId())).willReturn(java.util.Optional.of(account));
        given(decoder.decode("old-admin")).willReturn(Jwt.withTokenValue("old-admin").header("alg", "HS256")
                .subject(account.getId().toString()).claim("role", "ADMIN").build());
        mvc.perform(get("/api/v1/_test/admin").cookie(new Cookie("accessToken", "old-admin")))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingInactiveAndUnavailableDatabaseCannotAuthenticate() throws Exception {
        var account = com.nutee.backend.user.entity.User.create("tester", "test@example.com", "hash", "Test", true, null);
        given(decoder.decode("token")).willReturn(Jwt.withTokenValue("token").header("alg", "HS256").subject(account.getId().toString()).build());
        given(accountUsers.findById(account.getId())).willReturn(java.util.Optional.empty());
        mvc.perform(get("/api/v1/_test/jwt").cookie(new Cookie("accessToken", "token"))).andExpect(status().isUnauthorized());
        account.updateAccess("CUSTOMER", false);
        given(accountUsers.findById(account.getId())).willReturn(java.util.Optional.of(account));
        mvc.perform(get("/api/v1/_test/jwt").cookie(new Cookie("accessToken", "token"))).andExpect(status().isUnauthorized());
        given(accountUsers.findById(account.getId())).willThrow(new org.springframework.dao.DataAccessResourceFailureException("unavailable"));
        mvc.perform(get("/api/v1/_test/jwt").cookie(new Cookie("accessToken", "token"))).andExpect(status().isServiceUnavailable());
    }

    @RestController
    @TestComponent
    static class ProbeController {

        @GetMapping("/api/v1/_test/admin")
        @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
        String admin() {
            return "admin";
        }

        @GetMapping("/api/v1/_test/jwt")
        String secured() {
            return "verified";
        }
    }
}
