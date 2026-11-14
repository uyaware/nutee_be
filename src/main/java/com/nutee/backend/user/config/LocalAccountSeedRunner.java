package com.nutee.backend.user.config;

import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Tạo tài khoản quản trị mẫu chỉ trong profile local, không sửa tài khoản đã tồn tại. */
@Component
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class LocalAccountSeedRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    @Value("${LOCAL_ADMIN_PASSWORD:}")
    private String adminPassword;
    @Value("${LOCAL_STAFF_PASSWORD:}")
    private String staffPassword;
    private Path envFile = Path.of(".env");

    public void run(ApplicationArguments args) {
        seed("admin", "admin@nutee.local", "ADMIN", "LOCAL_ADMIN_PASSWORD", adminPassword);
        seed("staff", "staff@nutee.local", "STAFF", "LOCAL_STAFF_PASSWORD", staffPassword);
    }

    private void seed(String username, String email, String role, String passwordKey, String password) {
        if (users.existsByEmailIgnoreCase(email) || users.existsByUsernameIgnoreCase(username)) {
            log.info("Local {} account seed skipped: email or username already exists.", role);
            return;
        }
        if (password == null || password.isBlank()) {
            password = createPassword(passwordKey);
        }
        User user = User.create(username, email, passwords.encode(password), username, false, null);
        user.updateAccess(role, true);
        users.saveAndFlush(user);
        log.info("Local {} account created.", role);
    }

    private String createPassword(String key) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String password = Base64.getEncoder().encodeToString(bytes);
        try {
            Files.writeString(envFile, System.lineSeparator() + key + "=" + password + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot save local seed credentials in .env", exception);
        }
        return password;
    }
}
