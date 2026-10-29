package com.nutee.backend.auth.repository;

import com.nutee.backend.auth.entity.AuthProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tra cứu liên kết tài khoản theo provider và danh tính bên ngoài. */
public interface AuthProviderRepository extends JpaRepository<AuthProvider, Integer> {
    /** Tìm tài khoản NUTEE đã liên kết với danh tính của provider. */
    Optional<AuthProvider> findByProviderAndProviderUserId(String provider, String providerUserId);
    /** Kiểm tra tài khoản đã liên kết danh tính nào của provider này. */
    Optional<AuthProvider> findByUserIdAndProvider(UUID userId, String provider);
}
