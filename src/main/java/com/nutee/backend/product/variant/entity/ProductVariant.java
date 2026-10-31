package com.nutee.backend.product.variant.entity;

import com.nutee.backend.product.entity.Product;
import com.nutee.backend.product.image.entity.ProductImage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Biến thể có SKU duy nhất; quan hệ sản phẩm và ảnh mặc định LAZY. */
@Entity
@Table(name = "product_variants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id")
    private ProductImage image;
    @Column(nullable = false, unique = true, length = 16)
    private String sku;
    private Integer price;
    private Integer quantity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Trừ kho đã khóa khi chốt đơn, không cho âm tồn kho. */
    public void reserve(int amount) {
        if (quantity == null || amount < 1 || quantity < amount) throw new com.nutee.backend.common.exception.ApiException(org.springframework.http.HttpStatus.CONFLICT, "Tồn kho đã thay đổi.");
        quantity -= amount;
    }
    public void release(int amount) { quantity = Math.addExact(quantity, amount); }
}
