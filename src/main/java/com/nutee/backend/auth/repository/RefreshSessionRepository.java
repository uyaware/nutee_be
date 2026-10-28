package com.nutee.backend.auth.repository;

import com.nutee.backend.auth.entity.RefreshSession;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSession s where s.id = :id")
    Optional<RefreshSession> findForUpdate(UUID id);

    @Query("""
            select count(s) > 0 from RefreshSession s
            where s.id = :id and s.user.id = :userId and s.revokedAt is null
            and s.expiresAt > :now and s.user.active = true
            """)
    boolean isUsable(UUID id, UUID userId, Instant now);
}
