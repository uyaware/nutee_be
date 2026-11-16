package com.nutee.backend.security;

import org.springframework.security.authentication.BadCredentialsException;

/** Từ chối phiên đầy đủ khi email hiện tại trong database chưa được xác thực. */
public class EmailNotVerifiedException extends BadCredentialsException {
    public EmailNotVerifiedException() {
        super("Vui lòng xác thực email để tiếp tục.");
    }
}
