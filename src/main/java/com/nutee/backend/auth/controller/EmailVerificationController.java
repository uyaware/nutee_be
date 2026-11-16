package com.nutee.backend.auth.controller;

import com.nutee.backend.auth.dto.AuthResponse;
import com.nutee.backend.auth.dto.VerificationResponse;
import com.nutee.backend.auth.dto.VerifyEmailRequest;
import com.nutee.backend.auth.service.EmailVerificationService;
import com.nutee.backend.security.JwtCookies;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** API chờ xác thực chỉ chấp nhận cookie có mục đích xác thực email, không nhận email từ URL. */
@RestController
@RequestMapping("/api/v1/auth/verification")
@SecurityRequirements
@RequiredArgsConstructor
public class EmailVerificationController {
    private final EmailVerificationService verification;
    private final JwtCookies cookies;

    @GetMapping
    public ResponseEntity<VerificationResponse> status(
            @CookieValue(name = JwtCookies.VERIFICATION, required = false) String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(verification.status(token));
    }

    @PostMapping("/resend")
    public ResponseEntity<VerificationResponse> resend(
            @CookieValue(name = JwtCookies.VERIFICATION, required = false) String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(verification.resend(token));
    }

    @PostMapping("/confirm")
    public ResponseEntity<AuthResponse> confirm(
            @CookieValue(name = JwtCookies.VERIFICATION, required = false) String token,
            @Valid @RequestBody VerifyEmailRequest body, HttpServletRequest request, HttpServletResponse response) {
        AuthResponse session = verification.confirm(token, body.otp());
        cookies.login(request, response, session);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(session);
    }
}
