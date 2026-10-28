package com.nutee.backend.auth.controller;

import com.nutee.backend.auth.dto.GoogleLoginRequest;
import com.nutee.backend.auth.dto.LoginRequest;
import com.nutee.backend.auth.dto.RefreshRequest;
import com.nutee.backend.auth.dto.RegisterRequest;
import com.nutee.backend.auth.dto.TokenResponse;
import com.nutee.backend.auth.dto.UserResponse;
import com.nutee.backend.auth.service.AuthService;
import com.nutee.backend.auth.service.GoogleTokenVerifier;
import com.nutee.backend.security.GoogleProperties;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private static final String NONCE_COOKIE = "nutee_google_nonce";
    private final AuthService auth;
    private final GoogleTokenVerifier google;
    private final GoogleProperties googleProperties;
    private final SecureRandom random = new SecureRandom();

    @PostMapping(value = "/register", consumes = "application/json")
    @SecurityRequirements
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.created(URI.create("/api/v1/auth/me")).cacheControl(CacheControl.noStore())
                .body(auth.register(request));
    }

    @PostMapping(value = "/login", consumes = "application/json")
    @SecurityRequirements
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return tokenResponse(auth.login(request));
    }

    @PostMapping(value = "/refresh", consumes = "application/json")
    @SecurityRequirements
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return tokenResponse(auth.refresh(request.refreshToken()));
    }

    @PostMapping(value = "/logout", consumes = "application/json")
    @SecurityRequirements
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        auth.logout(request.refreshToken());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return auth.me(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/google/nonce")
    @SecurityRequirements
    public ResponseEntity<Map<String, String>> nonce() {
        google.requireEnabled();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie(nonce, Duration.ofMinutes(5)).toString())
                .body(Map.of("nonce", nonce));
    }

    @PostMapping(value = "/google", consumes = "application/json")
    @SecurityRequirements
    public ResponseEntity<TokenResponse> google(@Valid @RequestBody GoogleLoginRequest request,
            @CookieValue(name = NONCE_COOKIE, required = false) String nonce,
            HttpServletResponse response) {
        consumeNonce(nonce, response);
        return tokenResponse(auth.google(google.verify(request.idToken(), nonce)));
    }

    @PostMapping(value = "/google/link", consumes = "application/json")
    public UserResponse linkGoogle(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GoogleLoginRequest request,
            @CookieValue(name = NONCE_COOKIE, required = false) String nonce,
            HttpServletResponse response) {
        consumeNonce(nonce, response);
        return auth.linkGoogle(UUID.fromString(jwt.getSubject()), google.verify(request.idToken(), nonce));
    }

    private void consumeNonce(String nonce, HttpServletResponse response) {
        google.requireEnabled();
        if (nonce == null || !nonce.matches("[A-Za-z0-9_-]{43}")) {
            throw new BadCredentialsException("Google nonce is required.");
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(NONCE_COOKIE, value).httpOnly(true).secure(googleProperties.secureCookie())
                .sameSite("Lax").path("/api/v1/auth/google").maxAge(maxAge).build();
    }

    private ResponseEntity<TokenResponse> tokenResponse(TokenResponse tokens) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tokens);
    }
}
