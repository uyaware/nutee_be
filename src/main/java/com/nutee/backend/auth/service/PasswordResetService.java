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
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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
    private final JavaMailSender mail;
    private final String resetUrl;
    private final String from;
    private final boolean enabled;
    private final SecureRandom random = new SecureRandom();

    /** Nhận cấu hình gửi mail và kiểm tra URL khôi phục đáng tin cậy. */
    public PasswordResetService(UserRepository users, PasswordEncoder passwords, JavaMailSender mail,
            @Value("${app.password-reset.url}") String resetUrl,
            @Value("${app.password-reset.from}") String from,
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
        this.from = from;
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
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("NUTEE - Đặt lại mật khẩu");
        // Dùng fragment để token không xuất hiện trong HTTP access log hoặc Referer.
        message.setText("Mở liên kết sau để đặt lại mật khẩu (hết hạn sau 15 phút):\n"
                + resetUrl + "#token=" + token + "\nNếu bạn không yêu cầu, hãy bỏ qua email này.");
        try {
            mail.send(message);
            user.requestPasswordReset(hash(token), now.plus(TOKEN_TTL));
        } catch (MailException exception) {
            // Giữ phản hồi chung để không tiết lộ email có tài khoản; không log token hay nội dung mail.
            log.warn("Password reset email delivery failed");
        }
    }

    /** Tiêu thụ mã còn hạn trong transaction, đổi mật khẩu và hủy phiên refresh cũ. */
    @Transactional
    public void reset(String token, String password) {
        requireEnabled();
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mật khẩu tối đa 72 byte UTF-8.");
        }
        var user = users.findByPasswordResetHash(hash(token))
                .orElseThrow(PasswordResetService::invalidToken);
        if (!user.isActive() || user.getPasswordResetExpiresAt() == null
                || !user.getPasswordResetExpiresAt().isAfter(Instant.now())) {
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
        return new ApiException(HttpStatus.BAD_REQUEST, "Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Khôi phục mật khẩu qua email hiện chưa khả dụng.");
        }
    }
}
