package com.nutee.backend.aftersale.entity;

import com.nutee.backend.order.item.entity.OrderItem;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Yêu cầu hậu mãi gắn với dòng hàng đã mua; trạng thái nằm trong lịch sử. */
@Entity
@Table(name = "aftersale_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AfterSaleRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;
    private String type;
    @Column(columnDefinition = "text") private String reason;
    @Column(columnDefinition = "text") private String description;
    @Column(columnDefinition = "text") private String images;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at") private Instant updatedAt;

    public AfterSaleRequest(OrderItem item, String type, String reason, String description) {
        this.orderItem = item;
        this.type = type;
        this.reason = reason;
        this.description = description;
    }
}
