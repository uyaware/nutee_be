package com.nutee.backend.account.entity;

import com.nutee.backend.account.dto.AddressRequest;
import com.nutee.backend.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Địa chỉ thuộc một tài khoản; thông tin liên hệ lấy từ hồ sơ người dùng. */
@Entity
@Table(name = "user_addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAddress {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "province_city")
    private String provinceCity;
    @Column(name = "ward_commune")
    private String wardCommune;
    private String street;
    @Column(name = "is_default")
    private Boolean defaultAddress;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Tạo địa chỉ với trạng thái mặc định đã được service xác định dưới khóa tài khoản. */
    public UserAddress(User user, AddressRequest request, boolean isDefault) {
        this.user = user;
        update(request, isDefault);
    }

    /** Cập nhật thông tin giao hàng, không thay đổi người sở hữu địa chỉ. */
    public void update(AddressRequest request, boolean isDefault) {
        provinceCity = request.provinceCity();
        wardCommune = request.wardCommune();
        street = request.street();
        defaultAddress = isDefault;
    }

    public void setDefault(boolean value) {
        defaultAddress = value;
    }

    @PrePersist
    void initializeTimestamps() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
