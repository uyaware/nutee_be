package com.nutee.backend.promotion.repository;

import com.nutee.backend.promotion.entity.Promotion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;

/** Tra cứu rule và khóa khi sửa quan hệ sản phẩm để không ghi đè thay đổi đồng thời. */
public interface PromotionRepository extends JpaRepository<Promotion, UUID>, JpaSpecificationExecutor<Promotion> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Promotion> findLockedById(UUID id);
}
