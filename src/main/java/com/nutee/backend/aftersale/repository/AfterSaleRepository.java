package com.nutee.backend.aftersale.repository;

import com.nutee.backend.aftersale.entity.AfterSaleRequest;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.*;

/** Đọc hậu mãi theo trang và khóa yêu cầu khi ghi lịch sử xử lý. */
public interface AfterSaleRepository extends JpaRepository<AfterSaleRequest, Integer>, JpaSpecificationExecutor<AfterSaleRequest> {
    @Override
    @EntityGraph(attributePaths = {"orderItem", "orderItem.order", "orderItem.order.user", "orderItem.order.address", "orderItem.variant", "orderItem.variant.product"})
    Page<AfterSaleRequest> findAll(Specification<AfterSaleRequest> filter, Pageable pageable);

    @EntityGraph(attributePaths = {"orderItem", "orderItem.order", "orderItem.order.user", "orderItem.order.address", "orderItem.variant", "orderItem.variant.product"})
    Optional<AfterSaleRequest> findDetailedById(int id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AfterSaleRequest> findLockedById(int id);
    List<AfterSaleRequest> findByOrderItem_Id(int itemId);
}
