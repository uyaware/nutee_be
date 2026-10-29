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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import java.util.Arrays;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, TokenProperties.class, GoogleProperties.class})
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            SecurityErrorHandler errors, @Value("${app.docs-enabled:false}") boolean docsEnabled)
            throws Exception {
        http
                // Bearer tokens will use the Authorization header; no cookie authentication exists.
                .csrf(AbstractHttpConfigurer::disable)
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
                    authorize.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                            "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/google",
                            "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password").permitAll();
                    authorize.requestMatchers(HttpMethod.GET, "/api/v1/auth/google/nonce").permitAll();
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
        http.oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                .authenticationEntryPoint(errors).accessDeniedHandler(errors));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

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
        configuration.setAllowedMethods(List.of("GET", "POST", "HEAD", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
