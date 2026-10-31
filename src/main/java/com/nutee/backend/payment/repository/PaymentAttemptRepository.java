package com.nutee.backend.payment.repository;

import com.nutee.backend.payment.entity.PaymentAttempt;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

/** Mọi thao tác ghi lấy khóa đơn trước rồi khóa attempt theo cùng thứ tự. */
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    Optional<PaymentAttempt> findByOrderId(int orderId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<PaymentAttempt> findLockedByOrderId(int orderId);
    @Query("select p.orderId from PaymentAttempt p where p.gatewayOrderCode = :code") Optional<Integer> orderIdForCode(long code);
    List<PaymentAttempt> findByStatusInOrderByUpdatedAtAsc(List<String> statuses, Pageable pageable);
}
