package com.nutee.backend.aftersale.repository;

import com.nutee.backend.aftersale.entity.AfterSaleHistory;
import java.util.List;
import org.springframework.data.jpa.repository.*;

/** Tải lịch sử và người xử lý theo lô để tránh truy vấn lặp ở danh sách. */
public interface AfterSaleHistoryRepository extends JpaRepository<AfterSaleHistory, Integer> {
    @EntityGraph(attributePaths = "actor")
    List<AfterSaleHistory> findByRequestIdInOrderByIdAsc(List<Integer> requestIds);
}
