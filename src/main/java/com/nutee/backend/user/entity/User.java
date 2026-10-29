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

/** Entity tài khoản, lưu thông tin hồ sơ, mật khẩu đã băm và trạng thái phiên hiện tại. */
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
    @Column(name = "refresh_token", columnDefinition = "text")
    private String refreshToken;
    @Column(name = "password_reset_hash", length = 64)
    private String passwordResetHash;
    @Column(name = "password_reset_expires_at")
    private Instant passwordResetExpiresAt;
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

    /** Tạo tài khoản mới với vai trò CUSTOMER và trạng thái hoạt động mặc định. */
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

    /** Đánh dấu email đã được xác minh từ danh tính provider hợp lệ. */
    public void verifyEmail() {
        emailVerified = true;
    }

    /** Thay refresh token hiện tại hoặc xóa token khi đăng xuất. */
    public void replaceRefreshToken(String token) {
        refreshToken = token;
    }

    /** Lưu hash và thời hạn mã khôi phục, thay mã yêu cầu trước đó. */
    public void requestPasswordReset(String hash, Instant expiresAt) {
        passwordResetHash = hash;
        passwordResetExpiresAt = expiresAt;
    }

    /** Thay mật khẩu đã băm, đồng thời xóa mã khôi phục và refresh token. */
    public void resetPassword(String encoded) {
        password = encoded;
        refreshToken = null;
        passwordResetHash = null;
        passwordResetExpiresAt = null;
    }

    /** Gán thời điểm tạo/cập nhật khi tài khoản được lưu lần đầu. */
    @PrePersist
    void initializeTimestamps() {
        createdAt = updatedAt = Instant.now();
    }

    /** Cập nhật mốc thời gian khi thông tin tài khoản thay đổi. */
    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
