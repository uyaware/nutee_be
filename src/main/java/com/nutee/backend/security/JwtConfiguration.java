package com.nutee.backend.security;

import com.nutee.backend.auth.repository.RefreshSessionRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration(proxyBeanMethods = false)
public class JwtConfiguration {

    @Bean
    JwtDecoder jwtDecoder(HmacTokenService tokens, RefreshSessionRepository sessions) {
        return token -> {
            var jwt = tokens.decodeAccess(token);
            if (!sessions.isUsable(UUID.fromString(jwt.getClaimAsString("sid")),
                    UUID.fromString(jwt.getSubject()), Instant.now())) {
                throw new BadJwtException("The token session is not active.");
            }
            return jwt;
        };
    }
}
