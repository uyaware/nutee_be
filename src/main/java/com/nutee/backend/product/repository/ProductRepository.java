package com.nutee.backend.product.repository;

import com.nutee.backend.product.entity.Product;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Repository JPA cho sản phẩm, hỗ trợ Specification và tải quan hệ sau phân trang. */
public interface ProductRepository extends JpaRepository<Product, Integer>, JpaSpecificationExecutor<Product> {
    /** Fetch quan hệ cho các ID đã phân trang; không fetch collection trong query phân trang. */
    @EntityGraph(attributePaths = {"brand", "categories"})
    List<Product> findDistinctByIdIn(List<Integer> ids);
}
