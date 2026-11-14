package com.nutee.backend.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordResetServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final GmailService mail = mock(GmailService.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(4);
    private final PasswordResetService service = new PasswordResetService(users, passwords, mail,
            "http://localhost:5173/reset-password", true);
    private User user;

    @BeforeEach
    void configure() {
        user = User.create("tester", "test@example.com", "old-hash", "Test", false, null);
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(users.findLockedById(user.getId())).thenReturn(Optional.of(user));
        when(users.findByPasswordResetHash(anyString())).thenAnswer(invocation ->
                invocation.getArgument(0).equals(user.getPasswordResetHash()) ? Optional.of(user) : Optional.empty());
    }

    @Test
    void disabledRecoveryDoesNotAccessAccountsOrSendEmails() {
        var disabled = new PasswordResetService(users, passwords, mail,
                "http://localhost:5173/reset-password", false);
        assertThatThrownBy(() -> disabled.forgot("test@example.com")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> disabled.reset("test@example.com", "123456", "Password12345!")).isInstanceOf(ApiException.class);
        verifyNoInteractions(users, mail);
    }

    @Test
    void sendsOneTimeLinkStoresOnlyHashAndResetRevokesRefresh() {
        user.replaceRefreshToken("old-refresh");
        service.forgot(" TEST@EXAMPLE.COM ");
        var sent = ArgumentCaptor.forClass(String.class);
        verify(mail).sendPasswordResetOtp(eq(user.getEmail()), anyString(), sent.capture());
        String token = sent.getValue();
        assertThat(token).matches("[0-9]{6}");
        assertThat(user.getPasswordResetHash()).isEqualTo(PasswordResetService.hash(user.getId() + ":" + token)).isNotEqualTo(token);
        assertThat(user.getPasswordResetExpiresAt()).isAfter(Instant.now());
        service.reset(user.getEmail(), token, "ReplacementPassword123!");
        assertThat(passwords.matches("ReplacementPassword123!", user.getPassword())).isTrue();
        assertThat(user.getRefreshToken()).isNull();
        assertThat(user.getPasswordResetHash()).isNull();
        assertThat(user.getPasswordResetExpiresAt()).isNull();
        assertThatThrownBy(() -> service.reset(user.getEmail(), token, "ReplacementPassword123!"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void unknownAndGoogleOnlyAccountsDoNotSendMail() {
        when(users.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());
        service.forgot("unknown@example.com");
        User google = User.create("google", "google@example.com", null, null, true, null);
        when(users.findByEmailIgnoreCase(google.getEmail())).thenReturn(Optional.of(google));
        when(users.findLockedById(google.getId())).thenReturn(Optional.of(google));
        service.forgot(google.getEmail());
        verifyNoInteractions(mail);
    }

    @Test
    void cooldownPreventsDuplicateEmailAndNewLinkInvalidatesOldLink() {
        service.forgot(user.getEmail());
        String oldHash = user.getPasswordResetHash();
        service.forgot(user.getEmail());
        verify(mail, times(1)).sendPasswordResetOtp(anyString(), anyString(), anyString());
        user.requestPasswordReset(oldHash, Instant.now().plusSeconds(800));
        service.forgot(user.getEmail());
        verify(mail, times(2)).sendPasswordResetOtp(anyString(), anyString(), anyString());
        assertThat(user.getPasswordResetHash()).isNotEqualTo(oldHash);
    }

    @Test
    void expiredAndInvalidRequestsCannotChangePassword() {
        user.requestPasswordReset(PasswordResetService.hash(user.getId() + ":123456"), Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> service.reset(user.getEmail(), "123456", "ReplacementPassword123!")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.reset(user.getEmail(), "999999", "ReplacementPassword123!")).isInstanceOf(ApiException.class);
        assertThat(user.getPassword()).isEqualTo("old-hash");
    }

    @Test
    void resetRejectsTheCurrentPassword() {
        String current = "12345678";
        user.resetPassword(passwords.encode(current));
        user.requestPasswordReset(PasswordResetService.hash(user.getId() + ":123456"), Instant.now().plusSeconds(900));
        assertThatThrownBy(() -> service.reset(user.getEmail(), "123456", current))
                .isInstanceOf(ApiException.class);
        assertThat(passwords.matches(current, user.getPassword())).isTrue();
        service.reset(user.getEmail(), "123456", "newpassword");
        assertThat(passwords.matches(current, user.getPassword())).isFalse();
        assertThat(passwords.matches("newpassword", user.getPassword())).isTrue();
    }

    @Test
    void deliveryFailurePreservesPreviousLinkAndDoesNotExposeAccount() {
        doThrow(new GmailService.DeliveryException()).when(mail).sendPasswordResetOtp(anyString(), anyString(), anyString());
        assertThatCode(() -> service.forgot(user.getEmail())).doesNotThrowAnyException();
        assertThat(user.getPasswordResetHash()).isNull();
    }
    @Test
    void fiveWrongAttemptsInvalidateOtpAndResendResetsCounter() {
        service.forgot(user.getEmail());
        var sent = ArgumentCaptor.forClass(String.class);
        verify(mail).sendPasswordResetOtp(eq(user.getEmail()), anyString(), sent.capture());
        String correct = sent.getValue();
        String wrong = correct.equals("000000") ? "000001" : "000000";
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.reset(user.getEmail(), wrong, "ReplacementPassword123!"))
                    .isInstanceOf(ApiException.class);
        }
        assertThat(user.getPasswordResetAttempts()).isEqualTo(5);
        assertThat(user.getPasswordResetHash()).isNull();
        assertThatThrownBy(() -> service.reset(user.getEmail(), correct, "ReplacementPassword123!"))
                .isInstanceOf(ApiException.class);
        assertThat(user.getPassword()).isEqualTo("old-hash");
        user.requestPasswordReset(null, Instant.now().plusSeconds(800));
        service.forgot(user.getEmail());
        assertThat(user.getPasswordResetAttempts()).isZero();
        assertThat(user.getPasswordResetHash()).isNotNull();
    }

}
