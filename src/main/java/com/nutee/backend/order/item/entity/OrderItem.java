package com.nutee.backend.order.item.entity;

import com.nutee.backend.product.variant.entity.ProductVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Món hàng đã mua; cần ánh xạ này để nối đánh giá với biến thể bằng JPA. */
@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;
    @Column(nullable = false)
    private Integer quantity;
    @Column(name = "calculated_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal calculatedPrice;
    @Column(name = "warranty_expired_at")
    private Instant warrantyExpiredAt;
    @Column(name = "product_name") private String productName;
    @Column(name = "sku_snapshot", length = 16) private String skuSnapshot;
    @Column(name = "image_url", columnDefinition = "text") private String imageUrl;
    @Column(columnDefinition = "text") private String configuration;
    @Column(name = "original_price") private Long originalPrice;

    public OrderItem(int orderId, ProductVariant variant, int quantity, long price, long originalPrice,
            String name, String sku, String image, String configuration) {
        this.orderId = orderId; this.variant = variant; this.quantity = quantity; calculatedPrice = BigDecimal.valueOf(price);
        this.originalPrice = originalPrice; productName = name; skuSnapshot = sku; imageUrl = image; this.configuration = configuration;
    }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
