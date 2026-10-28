package com.nutee.backend.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String username;
    @Column(nullable = false)
    private String email;
    private String password;
    @Column(name = "full_name", length = 100)
    private String fullName;
    @Column(length = 10)
    private String phone;
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;
    @Column(name = "image_url", columnDefinition = "text")
    private String imageUrl;
    @Column(nullable = false)
    private String role;
    @Column(name = "is_active", nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static User create(String username, String email, String password, String fullName,
            boolean emailVerified, String imageUrl) {
        User user = new User();
        user.id = UUID.randomUUID();
        user.username = username;
        user.email = email;
        user.password = password;
        user.fullName = fullName;
        user.emailVerified = emailVerified;
        user.imageUrl = imageUrl;
        user.role = "CUSTOMER";
        user.active = true;
        return user;
    }

    public void verifyEmail() {
        emailVerified = true;
    }

    @PrePersist
    void initializeTimestamps() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
