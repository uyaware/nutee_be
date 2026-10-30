package com.nutee.backend.auth.dto;

import com.nutee.backend.user.entity.User;
import java.util.UUID;

/** Thông tin người dùng an toàn được phép gửi về frontend. */
public record UserResponse(UUID id, String username, String email, String fullName,
        boolean emailVerified, String imageUrl, String role) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(),
                user.isEmailVerified(), user.getImageUrl(), user.getRole());
    }
}
