package com.nutee.backend.brand.repository;

import com.nutee.backend.brand.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Set;
import com.nutee.backend.brand.dto.BrandResponse;

/** Dùng trực tiếp các method CRUD và sắp xếp có sẵn của Spring Data JPA. */
public interface BrandRepository extends JpaRepository<Brand, Integer> {
    /** Chỉ lấy thương hiệu của sản phẩm công khai có biến thể hợp lệ trong cây danh mục. */
    @Query("""
            select distinct new com.nutee.backend.brand.dto.BrandResponse(b.id, b.name)
            from ProductVariant v join v.product p join p.brand b join p.categories c
            where p.published = true and v.price >= 0 and v.quantity >= 0 and c.id in :categoryIds
            order by b.name, b.id
            """)
    List<BrandResponse> findFacetBrandsByCategoryIds(@Param("categoryIds") Set<Integer> categoryIds);
    @Query("select b.id, b.name, (select count(p) from Product p where p.brand.id = b.id) "
            + "from Brand b where lower(b.name) like lower(concat('%', :q, '%')) order by b.name, b.id")
    List<Object[]> findManagementItems(@Param("q") String query);
}
