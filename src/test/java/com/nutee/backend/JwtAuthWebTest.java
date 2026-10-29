package com.nutee.backend;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutee.backend.auth.controller.AuthController;
import com.nutee.backend.auth.controller.SocialLoginController;
import com.nutee.backend.auth.service.SocialIdentity;
import com.nutee.backend.auth.dto.AuthResponse;
import com.nutee.backend.auth.dto.UserResponse;
import com.nutee.backend.auth.service.AuthService;
import com.nutee.backend.auth.service.GoogleTokenVerifier;
import org.springframework.security.authentication.BadCredentialsException;
import com.nutee.backend.common.exception.GlobalExceptionHandler;
import com.nutee.backend.security.JwtCookies;
import com.nutee.backend.security.SecurityConfiguration;
import com.nutee.backend.security.SecurityErrorHandler;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {AuthController.class, SocialLoginController.class})
@Import({SecurityConfiguration.class, SecurityErrorHandler.class, JwtCookies.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class JwtAuthWebTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockitoBean private JwtDecoder decoder;
    @MockitoBean private AuthService auth;
    @MockitoBean private GoogleTokenVerifier google;
    private final UUID id = UUID.randomUUID();
    private final UserResponse user = new UserResponse(id, "tester", "test@example.com", "Test", false, null, "CUSTOMER");
    private final AuthResponse tokens = new AuthResponse("access-jwt", "refresh-jwt", 900, 604800, user);

    @BeforeEach
    void configure() {
        given(google.name()).willReturn("GOOGLE");
        given(auth.login(any())).willReturn(tokens);
        given(auth.register(any())).willReturn(tokens);
        given(auth.refresh("refresh-jwt")).willReturn(tokens);
        given(auth.refresh(isNull())).willThrow(new BadCredentialsException("Refresh token is required."));
        given(auth.me(id)).willReturn(user);
        given(decoder.decode("access-jwt")).willReturn(Jwt.withTokenValue("access-jwt")
                .header("alg", "HS256").subject(id.toString()).claim("role", "CUSTOMER")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postWithCsrf(String path) throws Exception {
        var response = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        var token = json.readTree(response.getContentAsString());
        return post(path).cookie(response.getCookie("XSRF-TOKEN"))
                .header(token.path("headerName").asText(), token.path("token").asText());
    }

    @Test
    void csrfBootstrapAndLoginSetHttpOnlyCookiesWithoutExposingJwtInJson() throws Exception {
        var csrfResponse = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse();
        Cookie csrfCookie = csrfResponse.getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.isHttpOnly()).isTrue();
        assertThat(csrfResponse.getCookie("JSESSIONID")).isNull();
        var bootstrap = json.readTree(csrfResponse.getContentAsString());
        var response = mvc.perform(post("/api/v1/auth/login").cookie(csrfCookie)
                .header(bootstrap.path("headerName").asText(), bootstrap.path("token").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"test@example.com\",\"password\":\"Password12345!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist()).andExpect(jsonPath("$.user.id").value(id.toString()))
                .andReturn().getResponse();
        for (String name : new String[]{"accessToken", "refreshToken"}) {
            Cookie cookie = response.getCookie(name);
            assertThat(cookie).isNotNull();
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.getPath()).isEqualTo("/api/v1");
            assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
            assertThat(cookie.getMaxAge()).isEqualTo(-1);
        }
        mvc.perform(get("/api/v1/auth/me").cookie(response.getCookie("accessToken"), response.getCookie("refreshToken")))
                .andExpect(status().isOk());
    }

    @Test
    void rememberMakesCookiesPersistentAndRefreshNeedsOnlyCookies() throws Exception {
        var response = mvc.perform(postWithCsrf("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"test@example.com\",\"password\":\"Password12345!\",\"remember\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getCookie("accessToken").getMaxAge()).isEqualTo(900);
        assertThat(response.getCookie("refreshToken").getMaxAge()).isEqualTo(604800);
        mvc.perform(postWithCsrf("/api/v1/auth/refresh")
                .cookie(new Cookie("refreshToken", "refresh-jwt"), new Cookie("accessToken", "expired-access"),
                        new Cookie("rememberLogin", "true")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().maxAge("refreshToken", 604800));
        verify(auth).refresh("refresh-jwt");
        verifyNoInteractions(decoder);
    }

    @Test
    void googleStrategyKeepsExistingLoginUrls() throws Exception {
        var identity = new SocialIdentity("provider-user", "test@example.com", "Test", null);
        given(google.verify(anyString(), anyString())).willReturn(identity);
        given(auth.socialLogin(anyString(), any())).willReturn(tokens);
        for (String path : new String[]{"/google", "/social/google"}) {
            var nonce = mvc.perform(get("/api/v1/auth" + path + "/nonce")).andExpect(status().isOk())
                    .andReturn().getResponse().getCookie("nutee_google_nonce");
            mvc.perform(postWithCsrf("/api/v1/auth" + path).cookie(nonce).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"idToken\":\"provider-credential\"}"))
                    .andExpect(status().isOk()).andExpect(cookie().httpOnly("accessToken", true))
                    .andExpect(jsonPath("$.accessToken").doesNotExist());
        }
        verify(auth, times(2)).socialLogin("GOOGLE", identity);
    }

    @Test
    void logoutWithoutBodyClearsCookiesEvenWithExpiredAccessOrRefresh() throws Exception {
        mvc.perform(postWithCsrf("/api/v1/auth/logout")
                .cookie(new Cookie("accessToken", "expired-access"), new Cookie("refreshToken", "expired-refresh")))
                .andExpect(status().isNoContent()).andExpect(cookie().maxAge("accessToken", 0))
                .andExpect(cookie().maxAge("refreshToken", 0)).andExpect(cookie().maxAge("rememberLogin", 0));
        mvc.perform(postWithCsrf("/api/v1/auth/logout")).andExpect(status().isNoContent());
        verifyNoInteractions(decoder);
    }

    @Test
    void missingCsrfRejectsLoginAndJwtAuthenticatedMutations() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"test@example.com\",\"password\":\"Password12345!\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/v1/auth/google/link").cookie(new Cookie("accessToken", "access-jwt"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"google-id\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        verifyNoInteractions(auth, decoder);
    }

    @Test
    void invalidRefreshClearsCookiesAndBodyCannotReplaceMissingCookie() throws Exception {
        mvc.perform(postWithCsrf("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"refresh-jwt\"}"))
                .andExpect(status().isUnauthorized()).andExpect(cookie().maxAge("accessToken", 0))
                .andExpect(cookie().maxAge("refreshToken", 0));
    }
}
