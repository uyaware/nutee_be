package com.nutee.backend.security;

import com.nutee.backend.auth.dto.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/** Đặt và xóa JWT trong cookie HttpOnly; việc xác thực JWT do JwtAuthenticationFilter thực hiện. */
@Component
@RequiredArgsConstructor
public class JwtCookies {
    public static final String ACCESS = "accessToken";
    public static final String REFRESH = "refreshToken";
    public static final String REMEMBER = "rememberLogin";
    public static final String PATH = "/api/v1";
    private final CookieProperties properties;
    private final CsrfTokenRepository csrfTokens;

    /** Đặt cookie cho phiên đăng nhập mới và yêu cầu trình duyệt lấy CSRF mới. */
    public void login(HttpServletRequest request, HttpServletResponse response, AuthResponse tokens, boolean remember) {
        issue(response, tokens, remember);
        csrfTokens.saveToken(null, request, response);
    }

    /** Ghi cặp JWT vào cookie HttpOnly; chỉ đặt Max-Age khi người dùng chọn ghi nhớ. */
    public void issue(HttpServletResponse response, AuthResponse tokens, boolean remember) {
        add(response, ACCESS, tokens.accessToken(), remember ? tokens.expiresIn() : null);
        add(response, REFRESH, tokens.refreshToken(), remember ? tokens.refreshExpiresIn() : null);
        add(response, REMEMBER, Boolean.toString(remember), remember ? tokens.refreshExpiresIn() : null);
    }

    /** Xóa cookie của phiên và CSRF, kể cả khi access token đã hết hạn. */
    public void clear(HttpServletRequest request, HttpServletResponse response) {
        add(response, ACCESS, "", 0L);
        add(response, REFRESH, "", 0L);
        add(response, REMEMBER, "", 0L);
        csrfTokens.saveToken(null, request, response);
    }

    private void add(HttpServletResponse response, String name, String value, Long maxAge) {
        var cookie = ResponseCookie.from(name, value).path(PATH).httpOnly(true)
                .secure(properties.secure()).sameSite(properties.sameSite());
        if (maxAge != null) {
            cookie.maxAge(maxAge);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
    }
}
