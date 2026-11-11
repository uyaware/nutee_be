package com.nutee.backend.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một lần đổi trạng thái; actor rỗng khi do hệ thống thực hiện. */
@Entity
@Table(name = "order_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(name = "order_id", nullable = false) private Integer orderId;
    @Column(name = "updated_by") private UUID updatedBy;
    @Column(name = "order_status") private String status;
    @Column(name = "occurred_at") private Instant occurredAt;

    public static OrderHistory create(int orderId, UUID actor, String status) {
        OrderHistory history = new OrderHistory();
        history.orderId = orderId;
        history.updatedBy = actor;
        history.status = status;
        history.occurredAt = Instant.now();
        return history;
    }
}
