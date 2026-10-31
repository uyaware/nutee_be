package com.nutee.backend.order.repository;

import com.nutee.backend.order.entity.Order;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;

/** Đếm đơn đang giữ/đã dùng mã; đơn hủy không chiếm lượt, không tạo lịch sử sử dụng giả. */
public interface OrderRepository extends JpaRepository<Order, Integer> {
    @EntityGraph(attributePaths = {"user", "address"})
    Optional<Order> findByUserIdAndIdempotencyKey(UUID userId, UUID key);
    @EntityGraph(attributePaths = {"user", "address"})
    Optional<Order> findByIdAndUserId(int id, UUID userId);
    @EntityGraph(attributePaths = {"user", "address"})
    @Query("select o from Order o where o.userId = :userId and (:status = 'all' or o.status = :status) "
            + "and lower(o.orderCode) like :search escape '!' order by o.createdAt desc, o.id desc")
    Page<Order> history(UUID userId, String status, String search, Pageable pageable);
    @Query("select o.id from Order o where o.idempotencyKey is not null and o.paymentMethod = 'qr' and o.status = 'pending' "
            + "and o.paymentStatus = 'pending' and o.paymentExpiresAt <= :now and not exists (select p.id from PaymentAttempt p where p.orderId = o.id) order by o.id")
    List<Integer> expiredUnstarted(java.time.Instant now, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<Order> findLockedById(int id);
    @Query("select count(o) from Order o where o.voucherId = :voucherId and (o.status is null or lower(o.status) not in ('cancelled', 'canceled'))")
    long countVoucherUses(UUID voucherId);
    @Query("select count(o) from Order o where o.voucherId = :voucherId and o.userId = :userId and (o.status is null or lower(o.status) not in ('cancelled', 'canceled'))")
    long countUserVoucherUses(UUID voucherId, UUID userId);
    boolean existsByVoucherId(UUID voucherId);

    interface UsageCount { UUID getVoucherId(); long getUses(); }
    /** Đếm theo lô để danh sách voucher không cần một truy vấn cho từng mã. */
    @Query("select o.voucherId as voucherId, count(o) as uses from Order o where o.voucherId in :ids "
            + "and (o.status is null or lower(o.status) not in ('cancelled', 'canceled')) group by o.voucherId")
    List<UsageCount> voucherUsage(List<UUID> ids);
    @Query("select o.voucherId as voucherId, count(o) as uses from Order o where o.voucherId in :ids and o.userId = :userId "
            + "and (o.status is null or lower(o.status) not in ('cancelled', 'canceled')) group by o.voucherId")
    List<UsageCount> userVoucherUsage(List<UUID> ids, UUID userId);
}
