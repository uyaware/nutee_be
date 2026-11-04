package com.nutee.backend.voucher.entity;

import com.nutee.backend.common.discount.Discounts;
import com.nutee.backend.user.entity.User;
import com.nutee.backend.voucher.dto.VoucherRequest;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Mã giảm giá đơn hàng; cấp riêng qua bảng user_vouchers hiện có. */
@Entity @Table(name = "vouchers") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Voucher {
    @Id private UUID id;
    @Column(length = 20) private String code;
    @Column(name = "applied_for") private String scope;
    private String type;
    private Integer value;
    @Column(name = "is_active") private boolean active;
    @Column(name = "start_date") private Instant startsAt;
    @Column(name = "end_date") private Instant endsAt;
    private Integer quantity;
    @Column(name = "max_uses_per_user") private Integer maxUsesPerUser;
    @Column(name = "min_order", nullable = false) private long minOrder;
    @Column(name = "max_discount") private Long maxDiscount;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_vouchers", joinColumns = @JoinColumn(name = "voucher_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> users = new LinkedHashSet<>();
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public Voucher(VoucherRequest request, List<User> recipients) { id = UUID.randomUUID(); update(request, recipients); }

    /** Thay rule và người được cấp mã nguyên tử; không thay lịch sử sử dụng. */
    public void update(VoucherRequest request, List<User> recipients) {
        Discounts.validate(request.type(), request.value(), request.startsAt(), request.endsAt());
        code = request.code(); type = request.type(); value = request.value(); scope = request.scope();
        active = request.isActive(); startsAt = request.startsAt(); endsAt = request.endsAt();
        minOrder = request.minOrder(); maxDiscount = request.maxDiscount();
        quantity = request.quantity(); maxUsesPerUser = request.maxUsesPerUser();
        users.clear(); users.addAll(recipients);
    }
    public void setActive(boolean enabled) { active = enabled; }
}
