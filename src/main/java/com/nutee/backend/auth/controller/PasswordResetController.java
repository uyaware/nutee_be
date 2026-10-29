package com.nutee.backend.auth.controller;

import com.nutee.backend.auth.dto.ForgotPasswordRequest;
import com.nutee.backend.auth.dto.ResetPasswordRequest;
import com.nutee.backend.auth.service.PasswordResetService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@SecurityRequirements
@RequiredArgsConstructor
public class PasswordResetController {
    private final PasswordResetService passwords;

    @PostMapping(value = "/forgot-password", consumes = "application/json")
    public ResponseEntity<Map<String, String>> forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        passwords.forgot(request.email());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("message",
                "Nếu email có tài khoản phù hợp, bạn sẽ nhận được liên kết đặt lại mật khẩu."));
    }

    @PostMapping(value = "/reset-password", consumes = "application/json")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        passwords.reset(request.token(), request.password());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
