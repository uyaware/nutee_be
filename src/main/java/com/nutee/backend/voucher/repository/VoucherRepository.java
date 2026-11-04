package com.nutee.backend.voucher.repository;

import com.nutee.backend.voucher.entity.Voucher;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** Tải người được cấp mã sau phân trang, không fetch collection trong query phân trang. */
public interface VoucherRepository extends JpaRepository<Voucher, UUID>, JpaSpecificationExecutor<Voucher> {
    @EntityGraph(attributePaths = "users") List<Voucher> findDistinctByIdIn(List<UUID> ids);
    @EntityGraph(attributePaths = "users") Optional<Voucher> findByCodeIgnoreCase(String code);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<Voucher> findLockedById(UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<Voucher> findLockedByCodeIgnoreCase(String code);

    /** EXISTS giữ phân trang đúng khi voucher được cấp cho nhiều người. */
    @Query("select v from Voucher v where v.active = true and v.startsAt <= :now and v.endsAt > :now "
            + "and (v.scope = 'all' or (v.scope = 'user' and exists (select u.id from Voucher granted join granted.users u "
            + "where granted.id = v.id and u.id = :userId))) order by v.code, v.id")
    Page<Voucher> available(@Param("userId") UUID userId, @Param("now") Instant now, Pageable pageable);
}
