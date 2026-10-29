package com.nutee.backend.auth.controller;

import com.nutee.backend.auth.dto.AuthResponse;
import com.nutee.backend.auth.dto.LoginRequest;
import com.nutee.backend.auth.dto.RegisterRequest;
import com.nutee.backend.auth.dto.UserResponse;
import com.nutee.backend.auth.service.AuthService;
import com.nutee.backend.security.JwtCookies;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

/** API đăng ký, đăng nhập bằng mật khẩu và quản lý phiên qua cookie. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    private final JwtCookies cookies;

    /** Cấp CSRF cho frontend để gửi trong header của các request thay đổi dữ liệu. */
    @GetMapping("/csrf")
    @SecurityRequirements
    public ResponseEntity<Map<String, String>> csrf(CsrfToken csrf) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("token", csrf.getToken(), "headerName", csrf.getHeaderName()));
    }

    /** Đăng ký tài khoản và đặt cookie đăng nhập; JSON tự ẩn hai JWT. */
    @PostMapping("/register")
    @SecurityRequirements
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        AuthResponse session = auth.register(body);
        cookies.login(request, response, session, false);
        return ResponseEntity.created(URI.create("/api/v1/auth/me")).cacheControl(CacheControl.noStore()).body(session);
    }

    /** Đăng nhập bằng email/username và áp dụng lựa chọn ghi nhớ đăng nhập. */
    @PostMapping("/login")
    @SecurityRequirements
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        AuthResponse session = auth.login(body);
        cookies.login(request, response, session, body.remember());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(session);
    }

    /** Xoay JWT bằng refresh cookie; không nhận token trong request body. */
    @PostMapping("/refresh")
    @SecurityRequirements
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = JwtCookies.REFRESH, required = false) String refreshToken,
            @CookieValue(name = JwtCookies.REMEMBER, defaultValue = "false") boolean remember,
            HttpServletRequest request, HttpServletResponse response) {
        try {
            AuthResponse session = auth.refresh(refreshToken);
            cookies.issue(response, session, remember);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(session);
        } catch (BadCredentialsException exception) {
            cookies.clear(request, response);
            throw exception;
        }
    }

    /** Thu hồi phiên hiện tại và xóa cookie; đăng xuất lại hoặc phiên hết hạn vẫn trả 204. */
    @PostMapping("/logout")
    @SecurityRequirements
    public ResponseEntity<Void> logout(
            @CookieValue(name = JwtCookies.REFRESH, required = false) String refreshToken,
            HttpServletRequest request, HttpServletResponse response) {
        auth.logout(refreshToken);
        cookies.clear(request, response);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    /** Trả thông tin người dùng từ access cookie đã được filter xác thực. */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(auth.me(UUID.fromString(jwt.getSubject())));
    }
}
