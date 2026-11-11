package com.nutee.backend.brand.repository;

import com.nutee.backend.brand.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

/** Dùng trực tiếp các method CRUD và sắp xếp có sẵn của Spring Data JPA. */
public interface BrandRepository extends JpaRepository<Brand, Integer> {
    @Query("select b.id, b.name, (select count(p) from Product p where p.brand.id = b.id) "
            + "from Brand b where lower(b.name) like lower(concat('%', :q, '%')) order by b.name, b.id")
    List<Object[]> findManagementItems(@Param("q") String query);
}
