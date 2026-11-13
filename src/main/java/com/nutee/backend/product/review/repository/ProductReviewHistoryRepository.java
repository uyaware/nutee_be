package com.nutee.backend.product.review.repository;

import com.nutee.backend.product.review.entity.ProductReviewHistory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Đọc bản sửa mới nhất và lưu lịch sử đánh giá. */
public interface ProductReviewHistoryRepository extends JpaRepository<ProductReviewHistory, Integer> {
    Optional<ProductReviewHistory> findFirstByReview_IdOrderByCreatedAtDescIdDesc(int reviewId);
}
