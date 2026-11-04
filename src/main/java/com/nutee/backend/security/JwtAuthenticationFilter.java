package com.nutee.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

/** Đọc access cookie và đưa người dùng đã xác thực vào SecurityContext của request. */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> PUBLIC_AUTH = Set.of("/api/v1/auth/csrf", "/api/v1/auth/register",
            "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/google",
            "/api/v1/auth/google/nonce", "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password");
    private final JwtDecoder decoder;
    private final JwtAuthenticationConverter converter;
    private final SecurityErrorHandler errors;

    /** Bỏ qua các route công khai để access token hết hạn không chặn login hoặc refresh. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("POST".equals(request.getMethod()) && "/api/v1/payments/payos/webhook".equals(path)) return true;
        if ("GET".equals(request.getMethod()) && (path.equals("/api/v1/products")
                || path.startsWith("/api/v1/products/") || path.equals("/api/v1/categories")
                || path.startsWith("/api/v1/categories/") || path.equals("/api/v1/brands")
                || path.startsWith("/api/v1/brands/"))) {
            return true;
        }
        return "OPTIONS".equals(request.getMethod()) || PUBLIC_AUTH.contains(path)
                || path.matches("/api/v1/auth/social/[^/]+(?:/nonce)?")
                || path.startsWith("/api/v1/actuator/")
                || path.startsWith("/api/v1/swagger-ui")
                || path.startsWith("/api/v1/api-docs");
    }

    /** Xác minh access JWT trong cookie trước khi chuyển request đến controller. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var cookie = WebUtils.getCookie(request, JwtCookies.ACCESS);
        if (cookie != null && !cookie.getValue().isBlank()) {
            try {
                var authentication = converter.convert(decoder.decode(cookie.getValue()));
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
                errors.commence(request, response, new BadCredentialsException("Invalid access cookie.", exception));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
