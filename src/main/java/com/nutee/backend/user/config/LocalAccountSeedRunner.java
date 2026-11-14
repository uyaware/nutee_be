package com.nutee.backend.user.config;

import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public void run(ApplicationArguments args) {
        seed("admin", "admin@nutee.local", "ADMIN");
        seed("staff", "staff@nutee.local", "STAFF");
    }

    private void seed(String username, String email, String role) {
        if (users.existsByEmailIgnoreCase(email) || users.existsByUsernameIgnoreCase(username)) {
            log.info("Local {} account seed skipped: email or username already exists.", role);
            return;
        }
        User user = User.create(username, email, passwords.encode("12345678"), username, false, null);
        user.updateAccess(role, true);
        users.saveAndFlush(user);
        log.info("Local {} account created.", role);
    }

}
