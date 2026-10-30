package com.nutee.backend.product.review.repository;

import com.nutee.backend.product.review.dto.ProductReviewResponse;
import com.nutee.backend.product.review.entity.ProductReview;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Repository JPA cho đánh giá qua món hàng đã mua, không đọc mật khẩu người dùng. */
public interface ProductReviewRepository extends JpaRepository<ProductReview, Integer> {
    /** Projection tổng số đánh giá đã có lịch sử của mỗi sản phẩm. */
    interface ProductCount {
        /** ID sản phẩm được tổng hợp. */
        Integer getProductId();
        /** Số đánh giá, mỗi review chỉ được tính một lần. */
        long getReviewCount();
    }

    /** Đếm các đánh giá có lịch sử của một sản phẩm. */
    long countByOrderItem_Variant_Product_IdAndHistoriesIsNotEmpty(int productId);

    /** Giữ JPQL vì COUNT/GROUP BY theo sản phẩm không biểu diễn bằng derived query thông thường. */
    @Query("""
            select r.orderItem.variant.product.id as productId, count(r) as reviewCount
            from ProductReview r where r.orderItem.variant.product.id in :ids
            and exists (select h.id from ProductReviewHistory h where h.review = r)
            group by r.orderItem.variant.product.id
            """)
    List<ProductCount> countByProductIds(List<Integer> ids);

    /** Giữ JPQL để chọn bản sửa cuối cho mỗi review trong một query, tránh N+1 và phân xử theo timestamp/ID. */
    @Query("""
            select new com.nutee.backend.product.review.dto.ProductReviewResponse(r.id,
                coalesce(nullif(trim(h.user.fullName), ''), h.user.username),
                coalesce(h.rating, 0), h.comment, h.createdAt)
            from ProductReview r join r.histories h where r.orderItem.variant.product.id = :productId
            and not exists (select later.id from ProductReviewHistory later where later.review = r
                and (later.createdAt > h.createdAt or (later.createdAt = h.createdAt and later.id > h.id)))
            order by h.createdAt desc, r.id desc
            """)
    List<ProductReviewResponse> findLatestByProductId(int productId, Pageable pageable);
}
