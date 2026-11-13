package com.nutee.backend.auth.service;

import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Xử lý mã khôi phục mật khẩu, gửi email và thu hồi refresh token sau khi đổi mật khẩu. */
@Service
@Slf4j
public class PasswordResetService {
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration RESEND_INTERVAL = Duration.ofMinutes(1);

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final GmailService mail;
    private final String resetUrl;
    private final boolean enabled;
    private final SecureRandom random = new SecureRandom();

    /** Nhận cấu hình gửi mail và kiểm tra URL khôi phục đáng tin cậy. */
    public PasswordResetService(UserRepository users, PasswordEncoder passwords, GmailService mail,
            @Value("${app.password-reset.url}") String resetUrl,
            @Value("${app.password-reset.enabled:false}") boolean enabled) {
        URI uri = URI.create(resetUrl);
        if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("PASSWORD_RESET_URL must be an absolute HTTP(S) URL without query or fragment.");
        }
        this.users = users;
        this.passwords = passwords;
        this.mail = mail;
        this.resetUrl = resetUrl;
        this.enabled = enabled;
    }

    /** Tạo mã một lần, gửi email và chỉ lưu hash; giới hạn gửi lại trong một phút. */
    @Transactional
    public void forgot(String email) {
        requireEnabled();
        var found = users.findByEmailIgnoreCase(email.strip().toLowerCase(Locale.ROOT));
        if (found.isEmpty()) {
            return;
        }
        var user = users.findLockedById(found.get().getId()).orElseThrow();
        if (!user.isActive() || user.getPassword() == null) {
            return;
        }
        Instant now = Instant.now();
        if (user.getPasswordResetExpiresAt() != null
                && user.getPasswordResetExpiresAt().isAfter(now.plus(TOKEN_TTL.minus(RESEND_INTERVAL)))) {
            return;
        }
        String otp;
        do {
            otp = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        } while (hash(user.getId() + ":" + otp).equals(user.getPasswordResetHash()));
        try {
            mail.sendPasswordResetOtp(user.getEmail(), resetUrl, otp);
            user.requestPasswordReset(hash(user.getId() + ":" + otp), now.plus(TOKEN_TTL));
        } catch (GmailService.DeliveryException exception) {
            // Giữ phản hồi chung để không tiết lộ email có tài khoản; không log token hay nội dung mail.
            log.warn("Password reset email delivery failed");
        }
    }

    /** Tiêu thụ mã còn hạn trong transaction, đổi mật khẩu và hủy phiên refresh cũ. */
    @Transactional(noRollbackFor = ApiException.class)
    public void reset(String email, String otp, String password) {
        requireEnabled();
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mật khẩu tối đa 72 byte UTF-8.");
        }
        var found = users.findByEmailIgnoreCase(email.strip().toLowerCase(Locale.ROOT))
                .orElseThrow(PasswordResetService::invalidToken);
        var user = users.findLockedById(found.getId()).orElseThrow(PasswordResetService::invalidToken);
        if (!user.isActive() || user.getPassword() == null || user.getPasswordResetHash() == null
                || user.getPasswordResetAttempts() >= 5 || user.getPasswordResetExpiresAt() == null
                || !user.getPasswordResetExpiresAt().isAfter(Instant.now())) {
            throw invalidToken();
        }
        if (otp == null || !otp.matches("[0-9]{6}") || !MessageDigest.isEqual(
                hash(user.getId() + ":" + otp).getBytes(StandardCharsets.UTF_8),
                user.getPasswordResetHash().getBytes(StandardCharsets.UTF_8))) {
            // noRollbackFor giữ bộ đếm nhập sai khi phản hồi lỗi; khóa dòng ngăn dò mã song song.
            user.rejectPasswordResetOtp();
            throw invalidToken();
        }
        user.resetPassword(passwords.encode(password));
    }

    /** Băm mã khôi phục bằng SHA-256 để không lưu mã gốc trong cơ sở dữ liệu. */
    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Email hoặc mã OTP không hợp lệ, đã hết hạn hoặc đã sai 5 lần. Hãy yêu cầu mã mới.");
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Khôi phục mật khẩu qua email hiện chưa khả dụng.");
        }
    }
}
