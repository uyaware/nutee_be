package com.nutee.backend.product.entity;

import com.nutee.backend.brand.entity.Brand;
import com.nutee.backend.category.entity.Category;
import com.nutee.backend.promotion.entity.Promotion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Sản phẩm gốc; quan hệ thương hiệu và danh mục được tải có chủ đích. */
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;
    @Column(columnDefinition = "text")
    private String description;
    @Column(name = "avg_rating", precision = 2, scale = 1)
    private BigDecimal avgRating;
    @Column(name = "sold_count")
    private Integer soldCount;
    @Column(name = "is_published")
    private Boolean published;
    @Column(name = "default_variant_id")
    private Integer defaultVariantId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    public void assignPromotion(Promotion rule) { promotion = rule; }

    public static Product create(String name, String description, boolean published, Brand brand, Set<Category> categories) {
        Product product = new Product();
        product.avgRating = BigDecimal.ZERO;
        product.soldCount = 0;
        product.update(name, description, published, brand, categories, null);
        return product;
    }

    public void update(String name, String description, boolean published, Brand brand, Set<Category> categories, Integer defaultVariantId) {
        this.name = name;
        this.description = description;
        this.published = published;
        this.brand = brand;
        this.categories.clear();
        this.categories.addAll(categories);
        this.defaultVariantId = defaultVariantId;
    }
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "product_categories", joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "cat_id"))
    private Set<Category> categories = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
