package com.nutee.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import java.util.Arrays;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Cấu hình bảo mật HTTP: cookie JWT, CSRF, phân quyền và CORS. */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, GoogleProperties.class, CookieProperties.class})
public class SecurityConfiguration {

    /** Thiết lập các route công khai và filter kiểm tra JWT; các thao tác ghi vẫn phải có CSRF. */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            SecurityErrorHandler errors, JwtDecoder decoder, CsrfTokenRepository csrfTokens,
            @Value("${app.docs-enabled:false}") boolean docsEnabled)
            throws Exception {
        http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens).ignoringRequestMatchers(request ->
                        "POST".equals(request.getMethod()) && "/api/v1/payments/payos/webhook".equals(request.getRequestURI())))
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(HttpMethod.POST, "/api/v1/payments/payos/webhook").permitAll();
                    authorize.requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/**",
                            "/api/v1/categories", "/api/v1/categories/*", "/api/v1/brands", "/api/v1/brands/*").permitAll();
                    authorize.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                            "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/google",
                            "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password",
                            "/api/v1/auth/social/*").permitAll();
                    authorize.requestMatchers(HttpMethod.GET, "/api/v1/auth/google/nonce", "/api/v1/auth/csrf",
                            "/api/v1/auth/social/*/nonce").permitAll();
                    authorize.requestMatchers(HttpMethod.GET,
                            "/api/v1/actuator/health", "/api/v1/actuator/health/liveness",
                            "/api/v1/actuator/health/readiness").permitAll();
                    if (docsEnabled) {
                        authorize.requestMatchers(HttpMethod.GET, "/api/v1/api-docs",
                                "/api/v1/api-docs/**", "/api/v1/api-docs.yaml",
                                "/api/v1/swagger-ui.html", "/api/v1/swagger-ui/**").permitAll();
                    }
                    authorize.anyRequest().authenticated();
                });
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        // JWT được gửi trong cookie nên vẫn phải kiểm tra CSRF cho request ghi dữ liệu.
        http.addFilterBefore(new JwtAuthenticationFilter(decoder, converter, errors),
                UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(HmacTokenService tokens) {
        return tokens::decodeAccess;
    }

    /** Lưu CSRF trong cookie HttpOnly; frontend lấy giá trị header qua endpoint /auth/csrf. */
    @Bean
    CsrfTokenRepository csrfTokenRepository(CookieProperties properties) {
        CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
        repository.setCookieCustomizer(cookie -> cookie.path(JwtCookies.PATH).httpOnly(true)
                .secure(properties.secure()).sameSite(properties.sameSite()));
        return repository;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Chỉ cho các frontend đã cấu hình gửi request kèm cookie và header CSRF. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.cors.allowed-origins:}") String origins) {
        List<String> allowed = Arrays.stream(origins.split(",")).map(String::strip)
                .filter(origin -> !origin.isBlank()).toList();
        if (allowed.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException("CORS origins must be explicit; wildcards are not allowed.");
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowed);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
