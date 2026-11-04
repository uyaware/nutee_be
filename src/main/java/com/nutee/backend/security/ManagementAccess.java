package com.nutee.backend.security;

import com.nutee.backend.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Kiểm tra quyền quản trị hiện tại trong DB, kể cả khi access JWT cũ chưa hết hạn. */
@Component
@RequiredArgsConstructor
public class ManagementAccess {
    private final UserRepository users;

    @Transactional(readOnly = true)
    public boolean isAdmin(Authentication authentication) {
        return users.findById(UUID.fromString(authentication.getName()))
                .filter(user -> user.isActive() && "ADMIN".equals(user.getRole())).isPresent();
    }
}
