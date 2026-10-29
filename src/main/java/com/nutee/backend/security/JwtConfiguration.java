package com.nutee.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration(proxyBeanMethods = false)
public class JwtConfiguration {

    @Bean
    JwtDecoder jwtDecoder(HmacTokenService tokens) {
        return tokens::decodeAccess;
    }
}
