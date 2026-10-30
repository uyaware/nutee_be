package com.nutee.backend.product.variant.specification.entity;

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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Thông số kỹ thuật thuộc đúng một biến thể. */
@Entity
@Table(name = "variant_specifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VariantSpecification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;
    @Column(name = "specification_name", nullable = false)
    private String specificationName;
    @Column(name = "specification_value", nullable = false, columnDefinition = "text")
    private String specificationValue;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
