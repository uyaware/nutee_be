package com.nutee.backend.aftersale.entity;

import com.nutee.backend.user.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một bước xử lý hậu mãi và tài khoản thực hiện, lưu trong cùng transaction. */
@Entity
@Table(name = "aftersale_request_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AfterSaleHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(name = "request_id", nullable = false) private Integer requestId;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false) private User actor;
    @Column(name = "request_status") private String status;
    @Column(name = "occurred_at") private Instant occurredAt;

    public AfterSaleHistory(int requestId, User actor, String status) {
        this.requestId = requestId;
        this.userId = actor.getId();
        this.actor = actor;
        this.status = status;
        this.occurredAt = Instant.now();
    }
}
