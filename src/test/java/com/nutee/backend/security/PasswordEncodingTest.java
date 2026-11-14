package com.nutee.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nutee.backend.auth.dto.RegisterRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordEncodingTest {
    @Test
    void supportsLongPasswordsAndExistingBcryptHashes() {
        var encoder = new SecurityConfiguration().passwordEncoder();
        String password = "a".repeat(100);
        String hash = encoder.encode(password);
        assertThat(encoder.matches(password, hash)).isTrue();
        assertThat(encoder.matches("a".repeat(99) + "b", hash)).isFalse();
        String unicodePassword = "ấ".repeat(100);
        assertThat(encoder.matches(unicodePassword, encoder.encode(unicodePassword))).isTrue();
        String oldHash = new BCryptPasswordEncoder(4).encode("a".repeat(72));
        assertThat(encoder.matches("a".repeat(72), oldHash)).isTrue();
        assertThat(encoder.matches("a".repeat(73), oldHash)).isFalse();
    }

    @Test
    void registrationAcceptsEightToOneHundredCharacters() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (int length : new int[] {7, 8, 100, 101}) {
                var request = new RegisterRequest(null, "test@example.com", "a".repeat(length), "Test");
                assertThat(validator.validate(request).isEmpty()).isEqualTo(length >= 8 && length <= 100);
            }
        }
    }
}
