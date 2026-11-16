package com.nutee.backend.auth.service;

import com.nutee.backend.auth.dto.AuthResponse;
import com.nutee.backend.auth.dto.VerificationResponse;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.security.HmacTokenService;
import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** OTP xác thực email gắn với cookie có mục đích riêng; khóa dòng để chống dùng mã đồng thời. */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private static final long OTP_TTL_SECONDS = 900;
    private static final long RESEND_SECONDS = 60;
    private final UserRepository users;
    private final HmacTokenService tokens;
    private final GmailService mail;
    private final SecureRandom random = new SecureRandom();

    /** Chỉ gọi sau khi đã kiểm tra mật khẩu hoặc danh tính provider và khóa tài khoản. */
    public AuthResponse begin(User user) {
        user.replaceRefreshToken(null);
        boolean sent = sendIfAllowed(user);
        return pending(user, sent, sent ? "Đã gửi mã xác thực email."
                : resendAfter(user) > 0 ? "Vui lòng kiểm tra thư hoặc chờ để gửi lại mã."
                : "Tài khoản đang chờ xác thực. Chưa gửi được email; bạn có thể gửi lại mã.");
    }

    /** Chuyển phiên cũ hợp lệ sang trạng thái chờ, không tự gửi mail từ request dữ liệu. */
    @Transactional
    public AuthResponse suspendAccess(UUID id) {
        User user = users.findLockedById(id).orElseThrow(EmailVerificationService::invalidPending);
        requirePendingAccount(user);
        user.replaceRefreshToken(null);
        return pending(user, false, "Vui lòng xác thực email để tiếp tục.");
    }

    @Transactional(readOnly = true)
    public VerificationResponse status(String token) {
        Jwt jwt = claims(token);
        User user = users.findById(UUID.fromString(jwt.getSubject())).orElseThrow(EmailVerificationService::invalidPending);
        requirePendingAccount(user);
        String message = user.getEmailVerificationAttempts() >= 5 ? "Mã đã bị vô hiệu sau 5 lần sai. Hãy yêu cầu mã mới."
                : user.getEmailVerificationHash() == null ? "Tài khoản đang chờ xác thực. Chưa gửi được email; bạn có thể gửi lại mã."
                : "Nhập mã OTP từ email hoặc yêu cầu gửi mã.";
        return information(user, remaining(jwt), false, message);
    }

    /** Gửi mã mới trong khóa dòng; gửi thất bại giữ mã cũ và không trả thành công giả. */
    @Transactional
    public VerificationResponse resend(String token) {
        Jwt jwt = claims(token);
        User user = locked(jwt);
        long wait = resendAfter(user);
        if (wait > 0) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Vui lòng chờ " + wait + " giây trước khi gửi lại mã.");
        }
        if (!sendIfAllowed(user)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa gửi được email xác thực. Vui lòng thử lại.");
        }
        return information(user, remaining(jwt), true, "Đã gửi mã xác thực mới. Mã cũ không còn hiệu lực.");
    }

    /** Bộ đếm sai phải được commit; chỉ request đầu tiên có OTP đúng được tạo phiên. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse confirm(String token, String otp) {
        User user = locked(claims(token));
        if (user.getEmailVerificationHash() == null || user.getEmailVerificationAttempts() >= 5
                || user.getEmailVerificationExpiresAt() == null
                || !user.getEmailVerificationExpiresAt().isAfter(Instant.now())) {
            throw invalidOtp();
        }
        if (otp == null || !otp.matches("[0-9]{6}") || !MessageDigest.isEqual(
                hash(user, otp).getBytes(StandardCharsets.UTF_8),
                user.getEmailVerificationHash().getBytes(StandardCharsets.UTF_8))) {
            user.rejectEmailVerificationOtp();
            throw invalidOtp();
        }
        user.verifyEmail();
        AuthResponse session = tokens.issue(user);
        user.replaceRefreshToken(session.refreshToken());
        return session;
    }

    private User locked(Jwt jwt) {
        User user = users.findLockedById(UUID.fromString(jwt.getSubject())).orElseThrow(EmailVerificationService::invalidPending);
        requirePendingAccount(user);
        return user;
    }

    private boolean sendIfAllowed(User user) {
        if (resendAfter(user) > 0) return false;
        String otp;
        do {
            otp = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        } while (hash(user, otp).equals(user.getEmailVerificationHash()));
        try {
            // Giữ khóa khi gửi để mã không bị xác nhận/ghi đè giữa hai request cùng tài khoản.
            mail.sendEmailVerificationOtp(user.getEmail(), otp);
        } catch (GmailService.DeliveryException exception) {
            return false;
        }
        user.requestEmailVerification(hash(user, otp), Instant.now().plusSeconds(OTP_TTL_SECONDS));
        return true;
    }

    private AuthResponse pending(User user, boolean sent, String message) {
        String token = tokens.issueVerification(user);
        return AuthResponse.pending(token, information(user, remaining(tokens.decodeVerification(token)), sent, message));
    }

    private VerificationResponse information(User user, long expiresIn, boolean sent, String message) {
        return new VerificationResponse(user.getEmail(), expiresIn, resendAfter(user), sent, message);
    }

    private long resendAfter(User user) {
        Instant expires = user.getEmailVerificationExpiresAt();
        if (expires == null) return 0;
        long millis = expires.minusSeconds(OTP_TTL_SECONDS - RESEND_SECONDS).toEpochMilli() - Instant.now().toEpochMilli();
        return Math.max(0, (millis + 999) / 1000);
    }

    private Jwt claims(String token) {
        try {
            if (token == null || token.isBlank()) throw invalidPending();
            return tokens.decodeVerification(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalidPending();
        }
    }

    private long remaining(Jwt jwt) {
        return Math.max(0, jwt.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond());
    }

    private static void requirePendingAccount(User user) {
        if (!user.isActive() || user.isEmailVerified()) throw invalidPending();
    }

    static String hash(User user, String otp) {
        return PasswordResetService.hash("email-verification:" + user.getId() + ":" + otp);
    }

    private static BadCredentialsException invalidPending() {
        return new BadCredentialsException("Verification state is invalid or expired.");
    }

    private static ApiException invalidOtp() {
        return new ApiException(HttpStatus.BAD_REQUEST,
                "Mã OTP không hợp lệ, đã hết hạn hoặc đã sai 5 lần. Hãy yêu cầu mã mới.");
    }
}
