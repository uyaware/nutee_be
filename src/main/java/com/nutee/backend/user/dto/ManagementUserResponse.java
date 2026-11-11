package com.nutee.backend.user.dto;

import com.nutee.backend.user.entity.User;
import java.util.Locale;
import java.util.UUID;

public record ManagementUserResponse(UUID id, String name, String email, String phone, String role, boolean isActive) {
    public static ManagementUserResponse from(User user) {
        return new ManagementUserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                user.getRole().toLowerCase(Locale.ROOT), user.isActive());
    }
}
