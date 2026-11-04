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
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordResetServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(4);
    private final PasswordResetService service = new PasswordResetService(users, passwords, mail,
            "http://localhost:5173/reset-password", "no-reply@nutee.local", true);
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
                "http://localhost:5173/reset-password", "no-reply@nutee.local", false);
        assertThatThrownBy(() -> disabled.forgot("test@example.com")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> disabled.reset("token", "Password12345!")).isInstanceOf(ApiException.class);
        verifyNoInteractions(users, mail);
    }

    @Test
    void sendsOneTimeLinkStoresOnlyHashAndResetRevokesRefresh() {
        user.replaceRefreshToken("old-refresh");
        service.forgot(" TEST@EXAMPLE.COM ");
        var sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        String token = sent.getValue().getText().split("#token=")[1].split("\n")[0];
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(user.getPasswordResetHash()).isEqualTo(PasswordResetService.hash(token)).isNotEqualTo(token);
        assertThat(user.getPasswordResetExpiresAt()).isAfter(Instant.now());
        service.reset(token, "ReplacementPassword123!");
        assertThat(passwords.matches("ReplacementPassword123!", user.getPassword())).isTrue();
        assertThat(user.getRefreshToken()).isNull();
        assertThat(user.getPasswordResetHash()).isNull();
        assertThat(user.getPasswordResetExpiresAt()).isNull();
        assertThatThrownBy(() -> service.reset(token, "ReplacementPassword123!"))
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
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
        user.requestPasswordReset(oldHash, Instant.now().plusSeconds(800));
        service.forgot(user.getEmail());
        verify(mail, times(2)).send(any(SimpleMailMessage.class));
        assertThat(user.getPasswordResetHash()).isNotEqualTo(oldHash);
    }

    @Test
    void expiredInvalidAndOversizedPasswordRequestsCannotChangePassword() {
        user.requestPasswordReset(PasswordResetService.hash("token"), Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> service.reset("token", "ReplacementPassword123!")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.reset("wrong", "ReplacementPassword123!")).isInstanceOf(ApiException.class);
        user.requestPasswordReset(PasswordResetService.hash("token"), Instant.now().plusSeconds(900));
        assertThatThrownBy(() -> service.reset("token", "ấ".repeat(30))).isInstanceOf(ApiException.class);
        assertThat(user.getPassword()).isEqualTo("old-hash");
    }

    @Test
    void deliveryFailurePreservesPreviousLinkAndDoesNotExposeAccount() {
        doThrow(new MailSendException("unavailable")).when(mail).send(any(SimpleMailMessage.class));
        assertThatCode(() -> service.forgot(user.getEmail())).doesNotThrowAnyException();
        assertThat(user.getPasswordResetHash()).isNull();
    }
}
