package com.nutee.backend.product.variant.specification.repository;

import com.nutee.backend.product.variant.specification.dto.VariantSpecificationResponse;
import com.nutee.backend.product.variant.specification.entity.VariantSpecification;
import java.util.List;
import java.util.Set;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Repository JPA cho thông số biến thể và projection dựng bộ lọc. */
public interface VariantSpecificationRepository extends JpaRepository<VariantSpecification, Integer> {
    List<VariantSpecification> findByVariant_IdOrderById(Integer variantId);
    List<VariantSpecification> findByVariant_IdIn(List<Integer> ids, Sort sort);

    /** Giữ JPQL để DISTINCT trên cặp tên/giá trị thay vì ID entity, đồng thời lọc biến thể công khai hợp lệ. */
    @Query("""
            select distinct new com.nutee.backend.product.variant.specification.dto.VariantSpecificationResponse(
                s.specificationName, s.specificationValue)
            from VariantSpecification s join s.variant v join v.product p
            where p.published = true and s.specificationName <> 'kind' and v.price >= 0 and v.quantity >= 0
            order by s.specificationName, s.specificationValue
            """)
    List<VariantSpecificationResponse> findFacetValues();

    /** Lọc cặp thông số theo danh mục và các danh mục con của nó. */
    @Query("""
            select distinct new com.nutee.backend.product.variant.specification.dto.VariantSpecificationResponse(
                s.specificationName, s.specificationValue)
            from VariantSpecification s join s.variant v join v.product p join p.categories c
            where p.published = true and s.specificationName <> 'kind' and v.price >= 0 and v.quantity >= 0
                and c.id in :categoryIds
            order by s.specificationName, s.specificationValue
            """)
    List<VariantSpecificationResponse> findFacetValuesByCategoryIds(@Param("categoryIds") Set<Integer> categoryIds);
}
