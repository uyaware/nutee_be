package com.nutee.backend.user.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class LocalAccountSeedRunnerTest {
    @Test
    void createsLocalAccountsWithEncodedPasswordsAndKeepsExistingAccounts() {
        UserRepository users = mock(UserRepository.class);
        var encoder = new BCryptPasswordEncoder(4);
        var seed = new LocalAccountSeedRunner(users, encoder);

        seed.run(null);
        var captured = ArgumentCaptor.forClass(User.class);
        verify(users, times(2)).saveAndFlush(captured.capture());
        var admin = captured.getAllValues().get(0);
        var staff = captured.getAllValues().get(1);
        assertThat(admin.getEmail()).isEqualTo("admin@nutee.local");
        assertThat(admin.getRole()).isEqualTo("ADMIN");
        assertThat(encoder.matches("12345678", admin.getPassword())).isTrue();
        assertThat(staff.getEmail()).isEqualTo("staff@nutee.local");
        assertThat(staff.getRole()).isEqualTo("STAFF");
        assertThat(encoder.matches("12345678", staff.getPassword())).isTrue();

        when(users.existsByEmailIgnoreCase(any())).thenReturn(true);
        seed.run(null);
        verify(users, times(2)).saveAndFlush(any());
    }
}
