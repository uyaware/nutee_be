package com.nutee.backend.product.variant.repository;

import com.nutee.backend.product.variant.entity.ProductVariant;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Đọc biến thể bằng Spring Data JPA và fetch ảnh theo lô. */
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Integer> {
    /** Chỉ lấy biến thể có giá/tồn kho hợp lệ; ảnh null vẫn được giữ trong kết quả. */
    @EntityGraph(attributePaths = "image")
    List<ProductVariant> findByProduct_IdInAndPriceGreaterThanEqualAndQuantityGreaterThanEqual(
            List<Integer> ids, int minimumPrice, int minimumQuantity, Sort sort);
}
