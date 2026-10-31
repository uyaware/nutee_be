package com.nutee.backend.payment.repository;
import com.nutee.backend.payment.entity.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
/** Unique reference tại database giữ xử lý webhook idempotent qua nhiều request/server. */
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, String> {}
