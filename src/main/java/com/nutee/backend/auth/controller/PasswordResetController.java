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

/** API yêu cầu email khôi phục và đặt lại mật khẩu bằng mã dùng một lần. */
@RestController
@RequestMapping("/api/v1/auth")
@SecurityRequirements
@RequiredArgsConstructor
public class PasswordResetController {
    private final PasswordResetService passwords;

    /** Chỉ trả thành công khi đã gửi mã; báo rõ email không tồn tại hoặc chỉ đăng nhập Google. */
    @PostMapping(value = "/forgot-password", consumes = "application/json")
    public ResponseEntity<Map<String, String>> forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        passwords.forgot(request.email());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("message",
                "Đã gửi mã OTP 6 số để đặt lại mật khẩu. Vui lòng kiểm tra email."));
    }

    @PostMapping(value = "/reset-password", consumes = "application/json")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        passwords.reset(request.email(), request.otp(), request.password());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
