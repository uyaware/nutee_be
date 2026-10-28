package com.nutee.backend.auth.service;

import org.springframework.security.authentication.BadCredentialsException;

/** This failure must commit any session revocation performed during reuse detection. */
public class InvalidRefreshTokenException extends BadCredentialsException {
    public InvalidRefreshTokenException() {
        super("Invalid refresh token.");
    }
}
