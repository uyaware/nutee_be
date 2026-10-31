package com.nutee.backend.payment.repository;

import com.nutee.backend.payment.entity.TransactionLog;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionLogRepository extends JpaRepository<TransactionLog, Integer> {
    Optional<TransactionLog> findFirstByProviderAndReferenceOrderByIdAsc(String provider, String reference);
}
