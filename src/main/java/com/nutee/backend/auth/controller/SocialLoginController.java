package com.nutee.backend.auth.controller;

import com.nutee.backend.auth.dto.AuthResponse;
import com.nutee.backend.auth.dto.SocialLoginRequest;
import com.nutee.backend.auth.dto.UserResponse;
import com.nutee.backend.auth.service.AuthService;
import com.nutee.backend.auth.service.SocialLoginProvider;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.security.JwtCookies;
import com.nutee.backend.security.CookieProperties;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Base64;
import java.util.Locale;
import java.security.SecureRandom;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.util.WebUtils;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** API social login dùng chung; Spring tự inject các Strategy đã đăng ký bằng @Service. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class SocialLoginController {
    private final List<SocialLoginProvider> providers;
    private final AuthService auth;
    private final JwtCookies cookies;
    private final CookieProperties cookieProperties;
    private final SecureRandom random = new SecureRandom();

    /** Cấp nonce; URL /google/nonce cũ là alias của /social/google/nonce. */
    @GetMapping({"/social/{provider}/nonce", "/{provider:google}/nonce"})
    @SecurityRequirements
    public ResponseEntity<Map<String, String>> nonce(@PathVariable String provider, HttpServletResponse response) {
        var strategy = provider(provider);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("nonce", createNonce(strategy.name(), response)));
    }

    /** Xác minh credential rồi cấp JWT; giữ alias /google và trường idToken cho frontend cũ. */
    @PostMapping({"/social/{provider}", "/{provider:google}"})
    @SecurityRequirements
    public ResponseEntity<AuthResponse> login(@PathVariable String provider,
            @Valid @RequestBody SocialLoginRequest body, @RequestParam(defaultValue = "false") boolean remember,
            HttpServletRequest request, HttpServletResponse response) {
        var strategy = provider(provider);
        String nonce = consumeNonce(strategy.name(), request, response);
        var identity = strategy.verify(body.credential(), nonce);
        AuthResponse session = auth.socialLogin(strategy.name(), identity);
        cookies.login(request, response, session, remember);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(session);
    }

    /** Liên kết provider với tài khoản hiện tại; /google/link cũ vẫn được giữ. */
    @PostMapping({"/social/{provider}/link", "/{provider:google}/link"})
    public UserResponse link(@PathVariable String provider, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SocialLoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        var strategy = provider(provider);
        String nonce = consumeNonce(strategy.name(), request, response);
        return auth.linkSocial(UUID.fromString(jwt.getSubject()), strategy.name(), strategy.verify(body.credential(), nonce));
    }

    /** Chọn Strategy đã đăng ký và kiểm tra provider đã bật; không dùng factory hay registry riêng. */
    private SocialLoginProvider provider(String name) {
        var strategy = providers.stream().filter(provider -> name.equalsIgnoreCase(provider.name())).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Unsupported login provider."));
        strategy.requireEnabled();
        return strategy;
    }

    /** Tạo nonce để ràng buộc credential Google với trình duyệt khởi tạo đăng nhập. */
    private String createNonce(String provider, HttpServletResponse response) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        nonceCookie(provider, nonce, 300, response);
        return nonce;
    }

    /** Đọc và xóa nonce trước khi Strategy xác minh credential của provider. */
    private String consumeNonce(String provider, HttpServletRequest request, HttpServletResponse response) {
        var cookie = WebUtils.getCookie(request, "nutee_" + provider.toLowerCase(Locale.ROOT) + "_nonce");
        if (cookie == null || !cookie.getValue().matches("[A-Za-z0-9_-]{43}")) {
            throw new BadCredentialsException("Provider nonce is required.");
        }
        nonceCookie(provider, "", 0, response);
        return cookie.getValue();
    }

    /** Ghi cookie nonce tạm thời với cùng cấu hình bảo vệ cookie của ứng dụng. */
    private void nonceCookie(String provider, String value, long maxAge, HttpServletResponse response) {
        var cookie = ResponseCookie.from("nutee_" + provider.toLowerCase(Locale.ROOT) + "_nonce", value)
                .path(JwtCookies.PATH).httpOnly(true).secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite()).maxAge(maxAge).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
