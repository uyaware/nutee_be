package com.nutee.backend.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.security.HmacTokenService;
import com.nutee.backend.security.JwtProperties;
import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;

class EmailVerificationServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final GmailService mail = mock(GmailService.class);
    private final HmacTokenService tokens = new HmacTokenService(new JwtProperties(
            "QUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUE=",
            "QkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkI=", Duration.ofMinutes(15), Duration.ofDays(7)));
    private final EmailVerificationService service = new EmailVerificationService(users, tokens, mail);
    private User user;

    @BeforeEach
    void configure() {
        user = User.create("tester", "test@example.com", "password-hash", "Test", false, null);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.findLockedById(user.getId())).thenReturn(Optional.of(user));
    }

    @Test
    void beginStoresOnlyPurposeBoundHashAndConsumesOtpBeforeIssuingSession() {
        user.requestPasswordReset("reset-hash", Instant.now().plusSeconds(900));
        user.replaceRefreshToken("old-session");
        var pending = service.begin(user);
        assertThat(pending.verificationRequired()).isTrue();
        assertThat(pending.user()).isNull();
        assertThat(pending.accessToken()).isNull();
        assertThat(pending.refreshToken()).isNull();
        assertThat(user.getRefreshToken()).isNull();
        var sent = ArgumentCaptor.forClass(String.class);
        verify(mail).sendEmailVerificationOtp(eq(user.getEmail()), sent.capture());
        assertThat(user.getEmailVerificationHash()).isEqualTo(EmailVerificationService.hash(user, sent.getValue()));
        assertThat(user.getEmailVerificationHash()).isNotEqualTo(PasswordResetService.hash(user.getId() + ":" + sent.getValue()));
        var session = service.confirm(pending.verificationToken(), sent.getValue());
        assertThat(session.user().emailVerified()).isTrue();
        assertThat(user.getRefreshToken()).isEqualTo(session.refreshToken());
        assertThat(user.getEmailVerificationHash()).isNull();
        assertThat(user.getPasswordResetHash()).isEqualTo("reset-hash");
        assertThatThrownBy(() -> service.confirm(pending.verificationToken(), sent.getValue()))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void fiveFailuresInvalidateOtpAndCooldownSurvivesTheLimit() {
        user.requestEmailVerification(EmailVerificationService.hash(user, "123456"), Instant.now().plusSeconds(900));
        String pending = tokens.issueVerification(user);
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThatThrownBy(() -> service.confirm(pending, "000000")).isInstanceOf(ApiException.class);
            assertThat(user.getEmailVerificationAttempts()).isEqualTo(attempt);
        }
        assertThat(user.getEmailVerificationHash()).isNull();
        assertThatThrownBy(() -> service.confirm(pending, "123456")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.resend(pending)).isInstanceOf(ApiException.class);
        verifyNoInteractions(mail);
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void expiredOtpCannotVerifyAndNewOtpReplacesPreviousCode() {
        user.requestEmailVerification(EmailVerificationService.hash(user, "123456"), Instant.now().minusSeconds(1));
        String pending = tokens.issueVerification(user);
        assertThatThrownBy(() -> service.confirm(pending, "123456")).isInstanceOf(ApiException.class);
        assertThat(service.resend(pending).mailSent()).isTrue();
        assertThatThrownBy(() -> service.confirm(pending, "123456")).isInstanceOf(ApiException.class);
    }

    @Test
    void failedDeliveryKeepsAccountPendingAndAllowsRetryWithoutRegistration() {
        doThrow(new GmailService.DeliveryException()).when(mail).sendEmailVerificationOtp(anyString(), anyString());
        var pending = service.begin(user);
        assertThat(pending.verificationRequired()).isTrue();
        assertThat(pending.verification().mailSent()).isFalse();
        assertThat(user.getEmailVerificationHash()).isNull();
        assertThatThrownBy(() -> service.resend(pending.verificationToken())).isInstanceOf(ApiException.class);
        doNothing().when(mail).sendEmailVerificationOtp(anyString(), anyString());
        assertThat(service.resend(pending.verificationToken()).mailSent()).isTrue();
    }

    @Test
    void fullSessionTokensAndMalformedTokensCannotBeUsedForVerification() {
        user.verifyEmail();
        var session = tokens.issue(user);
        for (String token : new String[] {session.accessToken(), session.refreshToken(), "invalid"}) {
            assertThatThrownBy(() -> service.status(token)).isInstanceOf(BadCredentialsException.class);
        }
        verifyNoInteractions(users, mail);
    }
}
