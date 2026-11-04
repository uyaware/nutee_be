package com.nutee.backend.auth.repository;

import com.nutee.backend.auth.entity.AuthProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tra cứu liên kết tài khoản theo provider và danh tính bên ngoài. */
public interface AuthProviderRepository extends JpaRepository<AuthProvider, Integer> {
    Optional<AuthProvider> findByProviderAndProviderUserId(String provider, String providerUserId);
    Optional<AuthProvider> findByUserIdAndProvider(UUID userId, String provider);
}
