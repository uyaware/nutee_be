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

    /** Yêu cầu gửi liên kết khôi phục; không tiết lộ email có tồn tại hay không. */
    @PostMapping(value = "/forgot-password", consumes = "application/json")
    public ResponseEntity<Map<String, String>> forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        passwords.forgot(request.email());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("message",
                "Nếu email có tài khoản phù hợp, bạn sẽ nhận được liên kết đặt lại mật khẩu."));
    }

    /** Đổi mật khẩu bằng mã hợp lệ và trả 204 khi hoàn tất. */
    @PostMapping(value = "/reset-password", consumes = "application/json")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        passwords.reset(request.token(), request.password());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
