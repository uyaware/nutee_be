package com.nutee.backend.auth.service;

import com.nutee.backend.auth.dto.LoginRequest;
import com.nutee.backend.auth.dto.RegisterRequest;
import com.nutee.backend.auth.dto.TokenResponse;
import com.nutee.backend.auth.dto.UserResponse;
import com.nutee.backend.auth.entity.AuthProvider;
import com.nutee.backend.auth.entity.RefreshSession;
import com.nutee.backend.auth.repository.AuthProviderRepository;
import com.nutee.backend.auth.repository.RefreshSessionRepository;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.security.HmacTokenService;
import com.nutee.backend.security.TokenProperties;
import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final AuthProviderRepository providers;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final HmacTokenService tokens;
    private final TokenProperties lifetimes;
    private final String dummyPassword;

    public AuthService(UserRepository users, AuthProviderRepository providers,
            RefreshSessionRepository sessions, PasswordEncoder passwords,
            HmacTokenService tokens, TokenProperties lifetimes) {
        this.users = users;
        this.providers = providers;
        this.sessions = sessions;
        this.passwords = passwords;
        this.tokens = tokens;
        this.lifetimes = lifetimes;
        dummyPassword = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String username = normalize(request.username());
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

    @Transactional
    public TokenResponse login(LoginRequest request) {
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

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResponse refresh(String token) {
        Jwt jwt = decodeRefresh(token);
        RefreshSession session = sessions.findForUpdate(UUID.fromString(jwt.getClaimAsString("sid")))
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = Instant.now();
        if (!session.getUser().getId().toString().equals(jwt.getSubject())
                || session.getRevokedAt() != null || !session.getExpiresAt().isAfter(now)
                || !session.getUser().isActive()) {
            throw new InvalidRefreshTokenException();
        }
        if (!MessageDigest.isEqual(session.getTokenHash().getBytes(StandardCharsets.US_ASCII),
                HmacTokenService.hash(token).getBytes(StandardCharsets.US_ASCII))) {
            session.revoke(now);
            throw new InvalidRefreshTokenException();
        }
        return rotate(session);
    }

    @Transactional
    public void logout(String token) {
        Jwt jwt = decodeRefresh(token);
        sessions.findForUpdate(UUID.fromString(jwt.getClaimAsString("sid"))).ifPresent(session -> {
            if (session.getUser().getId().toString().equals(jwt.getSubject())) {
                // A signed older token may still revoke its own session; never another user's session.
                session.revoke(Instant.now());
            }
        });
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return UserResponse.from(activeUser(userId));
    }

    @Transactional
    public TokenResponse google(GoogleIdentity identity) {
        AuthProvider provider = providers.findByProviderAndProviderUserId("GOOGLE", identity.subject()).orElse(null);
        if (provider != null) {
            User user = provider.getUser();
            requireActive(user);
            return startSession(user);
        }
        String email = normalize(identity.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Email is already registered. Sign in to that account and link Google first.");
        }
        User user = users.saveAndFlush(User.create("google_" + UUID.randomUUID().toString().replace("-", ""),
                email, null, identity.name(), true, identity.picture()));
        providers.saveAndFlush(new AuthProvider(user, identity.subject()));
        return startSession(user);
    }

    @Transactional
    public UserResponse linkGoogle(UUID userId, GoogleIdentity identity) {
        User user = activeUser(userId);
        if (!user.getEmail().equalsIgnoreCase(identity.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Google email must match the signed-in account email.");
        }
        AuthProvider existing = providers.findByProviderAndProviderUserId("GOOGLE", identity.subject()).orElse(null);
        if (existing != null && !existing.getUser().getId().equals(userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "Google identity is already linked to another account.");
        }
        AuthProvider linked = providers.findByUserIdAndProvider(userId, "GOOGLE").orElse(null);
        if (linked != null && !linked.getProviderUserId().equals(identity.subject())) {
            throw new ApiException(HttpStatus.CONFLICT, "This account already has a different Google identity.");
        }
        if (existing == null) { providers.saveAndFlush(new AuthProvider(user, identity.subject())); }
        user.verifyEmail();
        return UserResponse.from(user);
    }

    private TokenResponse startSession(User user) {
        Instant now = Instant.now();
        RefreshSession session = new RefreshSession(UUID.randomUUID(), user, now.plus(lifetimes.refreshTokenTtl()), now);
        TokenResponse response = rotate(session);
        sessions.save(session);
        return response;
    }

    private TokenResponse rotate(RefreshSession session) {
        var issued = tokens.issue(session.getUser(), session.getId(), session.getExpiresAt());
        session.rotate(HmacTokenService.hash(issued.refreshToken()), Instant.now());
        return new TokenResponse(issued.accessToken(), issued.refreshToken(), "Bearer", issued.expiresIn(),
                issued.refreshExpiresIn(), UserResponse.from(session.getUser()));
    }

    private Jwt decodeRefresh(String token) {
        try { return tokens.decodeRefresh(token); }
        catch (JwtException | IllegalArgumentException exception) { throw new InvalidRefreshTokenException(); }
    }

    private User activeUser(UUID id) {
        User user = users.findById(id).orElseThrow(() -> new BadCredentialsException("Invalid account."));
        requireActive(user);
        return user;
    }

    private void requireActive(User user) {
        if (!user.isActive()) { throw new BadCredentialsException("Invalid account."); }
    }

    private static String normalize(String value) { return value.strip().toLowerCase(Locale.ROOT); }
}
