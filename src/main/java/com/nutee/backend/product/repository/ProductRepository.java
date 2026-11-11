package com.nutee.backend.product.repository;

import com.nutee.backend.product.entity.Product;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

/** Repository JPA cho sản phẩm, hỗ trợ Specification và tải quan hệ sau phân trang. */
public interface ProductRepository extends JpaRepository<Product, Integer>, JpaSpecificationExecutor<Product> {
    long countByBrand_Id(Integer brandId);
    long countByCategories_Id(Integer categoryId);
    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Product> findLockedById(Integer id);
    /** Fetch quan hệ cho các ID đã phân trang; không fetch collection trong query phân trang. */
    @EntityGraph(attributePaths = {"brand", "categories", "promotion"})
    List<Product> findDistinctByIdIn(List<Integer> ids);

    @EntityGraph(attributePaths = "promotion")
    List<Product> findByPromotion_IdIn(List<UUID> ids);

    /** Khóa sản phẩm theo ID tăng dần để cập nhật assignment không tranh chấp hoặc deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Product> findLockedByIdIn(List<Integer> ids, Sort sort);
}
