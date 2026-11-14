package com.nutee.backend.user.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class LocalAccountSeedRunnerTest {
    @Test
    void generatesPasswordsInLocalEnvWhenNotConfigured(@TempDir Path directory) throws Exception {
        UserRepository users = mock(UserRepository.class);
        var encoder = new BCryptPasswordEncoder(4);
        var seed = new LocalAccountSeedRunner(users, encoder);
        Path env = directory.resolve(".env");
        ReflectionTestUtils.setField(seed, "envFile", env);

        seed.run(null);
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(env)) {
            properties.load(reader);
        }
        var captured = ArgumentCaptor.forClass(User.class);
        verify(users, times(2)).saveAndFlush(captured.capture());
        String adminPassword = properties.getProperty("LOCAL_ADMIN_PASSWORD");
        String staffPassword = properties.getProperty("LOCAL_STAFF_PASSWORD");
        assertThat(adminPassword).isNotBlank().isNotEqualTo(staffPassword);
        assertThat(encoder.matches(adminPassword, captured.getAllValues().get(0).getPassword())).isTrue();
        assertThat(encoder.matches(staffPassword, captured.getAllValues().get(1).getPassword())).isTrue();
    }

    @Test
    void createsLocalAccountsWithEncodedPasswordsAndKeepsExistingAccounts() {
        UserRepository users = mock(UserRepository.class);
        var encoder = new BCryptPasswordEncoder(4);
        var seed = new LocalAccountSeedRunner(users, encoder);
        ReflectionTestUtils.setField(seed, "adminPassword", "admin-test-password");
        ReflectionTestUtils.setField(seed, "staffPassword", "staff-test-password");

        seed.run(null);
        var captured = ArgumentCaptor.forClass(User.class);
        verify(users, times(2)).saveAndFlush(captured.capture());
        var admin = captured.getAllValues().get(0);
        var staff = captured.getAllValues().get(1);
        assertThat(admin.getEmail()).isEqualTo("admin@nutee.local");
        assertThat(admin.getRole()).isEqualTo("ADMIN");
        assertThat(encoder.matches("admin-test-password", admin.getPassword())).isTrue();
        assertThat(staff.getEmail()).isEqualTo("staff@nutee.local");
        assertThat(staff.getRole()).isEqualTo("STAFF");
        assertThat(encoder.matches("staff-test-password", staff.getPassword())).isTrue();

        when(users.existsByEmailIgnoreCase(any())).thenReturn(true);
        seed.run(null);
        verify(users, times(2)).saveAndFlush(any());
    }
}
