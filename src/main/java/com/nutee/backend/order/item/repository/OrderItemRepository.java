package com.nutee.backend.order.item.repository;

import com.nutee.backend.order.item.entity.OrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Đọc các dòng snapshot theo đơn và tải variant phục vụ hoàn kho theo lô. */
public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {
    interface Reservation { Integer getVariantId(); Integer getQuantity(); }
    @Query("select i.variant.id as variantId, i.quantity as quantity from OrderItem i where i.orderId = :orderId")
    List<Reservation> reservations(int orderId);
    @EntityGraph(attributePaths = {"variant", "variant.product"})
    List<OrderItem> findByOrderIdOrderByIdAsc(int orderId);
    @EntityGraph(attributePaths = {"variant", "variant.product"})
    List<OrderItem> findByOrderIdInOrderByIdAsc(List<Integer> orderIds);
}
