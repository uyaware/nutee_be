package com.nutee.backend.auth.service;

import com.nutee.backend.auth.dto.AuthResponse;
import com.nutee.backend.auth.dto.LoginRequest;
import com.nutee.backend.auth.dto.RegisterRequest;
import com.nutee.backend.auth.dto.UserResponse;
import com.nutee.backend.auth.entity.AuthProvider;
import com.nutee.backend.auth.repository.AuthProviderRepository;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.security.HmacTokenService;
import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Xử lý đăng ký, đăng nhập, phiên JWT và liên kết tài khoản bên thứ ba. */
@Service
@Transactional
public class AuthService {
    private final UserRepository users;
    private final AuthProviderRepository providers;
    private final PasswordEncoder passwords;
    private final HmacTokenService tokens;
    private final String dummyPassword;

    /** Nhận các dependency và tạo hash giả để kiểm tra mật khẩu cả khi tài khoản không tồn tại. */
    public AuthService(UserRepository users, AuthProviderRepository providers,
            PasswordEncoder passwords, HmacTokenService tokens) {
        this.users = users;
        this.providers = providers;
        this.passwords = passwords;
        this.tokens = tokens;
        this.dummyPassword = passwords.encode(UUID.randomUUID().toString());
    }

    /** Tạo tài khoản CUSTOMER bằng email/mật khẩu rồi mở phiên đăng nhập. */
    public AuthResponse register(RegisterRequest request) {
        String username = request.username() == null ? "user_" + UUID.randomUUID().toString().replace("-", "")
                : normalize(request.username());
        String email = normalize(request.email());
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes.");
        }
        if (users.existsByEmailIgnoreCase(email) || users.existsByUsernameIgnoreCase(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email or username is already registered.");
        }
        User user = users.saveAndFlush(User.create(username, email, passwords.encode(request.password()),
                request.fullName(), false, null));
        return startSession(user);
    }

    /** Kiểm tra email hoặc username và mật khẩu trước khi cấp cặp JWT mới. */
    public AuthResponse login(LoginRequest request) {
        String identifier = normalize(request.identifier());
        User user = (identifier.contains("@") ? users.findByEmailIgnoreCase(identifier)
                : users.findByUsernameIgnoreCase(identifier)).orElse(null);
        String encoded = user != null && user.getPassword() != null ? user.getPassword() : dummyPassword;
        boolean correct = request.password().getBytes(StandardCharsets.UTF_8).length <= 72
                && passwords.matches(request.password(), encoded);
        if (!correct || user == null || user.getPassword() == null || !user.isActive()) {
            throw new BadCredentialsException("Invalid credentials.");
        }
        return startSession(user);
    }

    /** Xác minh refresh token hiện tại và xoay cặp JWT, không kéo dài hạn phiên ban đầu. */
    public AuthResponse refresh(String token) {
        Jwt jwt = refreshClaims(token);
        User user = users.findLockedById(UUID.fromString(jwt.getSubject()))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token."));
        if (!user.isActive() || !matchesRefreshToken(user, token)) {
            throw new BadCredentialsException("Invalid refresh token.");
        }
        return storeTokens(user, tokens.issue(user, jwt.getExpiresAt()));
    }

    /** Thu hồi refresh token khớp phiên hiện tại; token thiếu, sai hoặc hết hạn được bỏ qua. */
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        Jwt jwt;
        try {
            jwt = tokens.decodeRefresh(token);
        } catch (JwtException | IllegalArgumentException exception) {
            return;
        }
        users.findLockedById(UUID.fromString(jwt.getSubject())).ifPresent(user -> {
            if (matchesRefreshToken(user, token)) {
                user.replaceRefreshToken(null);
            }
        });
    }

    /** Lấy thông tin tài khoản còn hoạt động, không trả mật khẩu hoặc token. */
    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return UserResponse.from(activeUser(userId));
    }

    /** Đăng nhập hoặc tạo tài khoản từ danh tính đã được Strategy của provider xác minh. */
    public AuthResponse socialLogin(String provider, SocialIdentity identity) {
        AuthProvider linked = providers.findByProviderAndProviderUserId(provider, identity.subject()).orElse(null);
        if (linked != null) {
            return startSession(linked.getUser());
        }

        String email = normalize(identity.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Email is already registered. Sign in to that account and link " + provider + " first.");
        }
        String username = provider.toLowerCase(Locale.ROOT) + "_" + UUID.randomUUID().toString().replace("-", "");
        User user = users.saveAndFlush(User.create(username, email, null, identity.name(), true, identity.picture()));
        providers.saveAndFlush(new AuthProvider(user, provider, identity.subject()));
        return startSession(user);
    }

    /** Liên kết provider với tài khoản đang đăng nhập; email phải khớp và liên kết không được thuộc người khác. */
    public UserResponse linkSocial(UUID userId, String provider, SocialIdentity identity) {
        User user = activeUser(userId);
        if (!user.getEmail().equalsIgnoreCase(identity.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Provider email must match the signed-in account email.");
        }
        AuthProvider existing = providers.findByProviderAndProviderUserId(provider, identity.subject()).orElse(null);
        if (existing != null && !existing.getUser().getId().equals(userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Provider identity is already linked to another account.");
        }
        AuthProvider linked = providers.findByUserIdAndProvider(userId, provider).orElse(null);
        if (linked != null && !linked.getProviderUserId().equals(identity.subject())) {
            throw new ApiException(HttpStatus.CONFLICT, "This account already has a different identity for this provider.");
        }
        if (existing == null) {
            providers.saveAndFlush(new AuthProvider(user, provider, identity.subject()));
        }
        user.verifyEmail();
        return UserResponse.from(user);
    }

    /** Khóa và kiểm tra lại tài khoản trước khi thay phiên đăng nhập cũ bằng phiên mới. */
    private AuthResponse startSession(User user) {
        User locked = users.findLockedById(user.getId())
                .orElseThrow(() -> new BadCredentialsException("Invalid account."));
        if (!locked.isActive()) {
            throw new BadCredentialsException("Invalid account.");
        }
        return storeTokens(locked, tokens.issue(locked));
    }

    private AuthResponse storeTokens(User user, AuthResponse issued) {
        user.replaceRefreshToken(issued.refreshToken());
        return issued;
    }

    /** So sánh refresh token theo thời gian cố định để kiểm tra đúng phiên hiện tại. */
    private boolean matchesRefreshToken(User user, String token) {
        return user.getRefreshToken() != null && MessageDigest.isEqual(
                user.getRefreshToken().getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    private Jwt refreshClaims(String token) {
        if (token == null || token.isBlank()) {
            throw new BadCredentialsException("Refresh token is required.");
        }
        try {
            return tokens.decodeRefresh(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BadCredentialsException("Invalid refresh token.");
        }
    }

    private User activeUser(UUID id) {
        User user = users.findById(id).orElseThrow(() -> new BadCredentialsException("Invalid account."));
        if (!user.isActive()) {
            throw new BadCredentialsException("Invalid account.");
        }
        return user;
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
