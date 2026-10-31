package com.nutee.backend.promotion.entity;

import com.nutee.backend.common.discount.Discounts;
import com.nutee.backend.promotion.dto.PromotionRequest;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Rule giảm giá sản phẩm, không ghi đè giá gốc trong product_variants. */
@Entity
@Table(name = "promotions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Promotion {
    @Id private UUID id;
    @Column(length = 20) private String code;
    @Column(name = "applied_for") private String scope;
    private String type;
    private Integer value;
    @Column(name = "is_active") private boolean active;
    @Column(name = "start_date") private Instant startsAt;
    @Column(name = "end_date") private Instant endsAt;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public Promotion(PromotionRequest request) {
        id = UUID.randomUUID();
        update(request);
    }

    /** Chỉ lưu rule hợp lệ; khóa row và kiểm tra quan hệ sản phẩm do service đảm nhiệm. */
    public void update(PromotionRequest request) {
        Discounts.validate(request.type(), request.value(), request.startsAt(), request.endsAt());
        code = request.code(); type = request.type(); value = request.value();
        scope = "product"; active = request.isActive(); startsAt = request.startsAt(); endsAt = request.endsAt();
    }

    public void setActive(boolean enabled) { active = enabled; }

    /** Tính giá hiện tại theo VND, bỏ qua rule legacy thiếu dữ liệu hoặc chưa có hiệu lực. */
    public int price(int original, Instant now) {
        if (value == null || value < 1 || ("percent".equals(type) && value > 100)
                || !"product".equals(scope) || !"active".equals(Discounts.status(active, startsAt, endsAt, now))) return original;
        return Math.toIntExact(Math.max(0, original - Discounts.amount(original, type, value)));
    }
}
