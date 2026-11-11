package com.nutee.backend.order.repository;

import com.nutee.backend.order.entity.OrderHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderHistoryRepository extends JpaRepository<OrderHistory, Integer> {
    List<OrderHistory> findByOrderIdOrderByOccurredAtAscIdAsc(int orderId);
}
