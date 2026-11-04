package com.nutee.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nutee.backend.auth.repository.AuthProviderRepository;
import com.nutee.backend.security.HmacTokenService;
import com.nutee.backend.security.JwtProperties;
import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.BadCredentialsException;

class AuthServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final HmacTokenService tokens = new HmacTokenService(new JwtProperties(
            "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
            "QkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkI=", Duration.ofMinutes(15), Duration.ofDays(7)));
    private final AuthService auth = new AuthService(users, mock(AuthProviderRepository.class),
            mock(PasswordEncoder.class), tokens);
    private User user;
    private String original;

    @BeforeEach
    void configure() {
        user = User.create("tester", "test@example.com", "hash", "Test", false, null);
        original = tokens.issue(user).refreshToken();
        user.replaceRefreshToken(original);
        given(users.findLockedById(user.getId())).willReturn(Optional.of(user));
    }

    @Test
    void refreshRotatesStoredTokenWithoutExtendingExpiry() {
        var response = auth.refresh(original);
        assertThat(response.refreshToken()).isNotEqualTo(original).isEqualTo(user.getRefreshToken());
        assertThat(tokens.decodeRefresh(response.refreshToken()).getExpiresAt())
                .isEqualTo(tokens.decodeRefresh(original).getExpiresAt());
        assertThat(tokens.decodeAccess(response.accessToken()).getClaimAsString("email")).isEqualTo(user.getEmail());
        assertThatThrownBy(() -> auth.refresh(original)).isInstanceOf(BadCredentialsException.class);
        assertThat(user.getRefreshToken()).isEqualTo(response.refreshToken());
        auth.refresh(response.refreshToken());
    }

    @Test
    void logoutClearsCurrentTokenAndLeavesSignedAccessUsable() {
        var response = auth.refresh(original);
        auth.logout(response.refreshToken());
        auth.logout(response.refreshToken());
        assertThat(user.getRefreshToken()).isNull();
        assertThatThrownBy(() -> auth.refresh(response.refreshToken())).isInstanceOf(BadCredentialsException.class);
        assertThat(tokens.decodeAccess(response.accessToken()).getSubject()).isEqualTo(user.getId().toString());
    }

    @Test
    void staleLogoutCannotClearReplacementToken() {
        var response = auth.refresh(original);
        auth.logout(original);
        assertThat(user.getRefreshToken()).isEqualTo(response.refreshToken());
    }

    @Test
    void expiredStoredTokenIsRejectedBeforeDatabaseLookup() {
        String expired = tokens.issue(user, Instant.now().minusSeconds(60)).refreshToken();
        user.replaceRefreshToken(expired);
        assertThatThrownBy(() -> auth.refresh(expired)).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(users);
    }

    @Test
    void invalidTokensCannotRefreshAndLogoutRemainsIdempotent() {
        for (String invalid : new String[]{tokens.issue(user).accessToken(), "invalid-token"}) {
            assertThatThrownBy(() -> auth.refresh(invalid)).isInstanceOf(BadCredentialsException.class);
            auth.logout(invalid);
        }
        verifyNoInteractions(users);
    }
}
