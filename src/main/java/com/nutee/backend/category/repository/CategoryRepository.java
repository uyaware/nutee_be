package com.nutee.backend.category.repository;

import com.nutee.backend.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;
import com.nutee.backend.category.dto.ManagementTaxonomyItem;
import org.springframework.data.repository.query.Param;

/** Dùng method có sẵn của Spring Data JPA để đọc danh mục. */
public interface CategoryRepository extends JpaRepository<Category, Integer> {
    long countByParent_Id(Integer parentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Category c order by c.id")
    List<Category> findAllLocked();

    @Query("select new com.nutee.backend.category.dto.ManagementTaxonomyItem(c.id, c.name, parent.id, "
            + "(select count(p) from Product p join p.categories pc where pc.id = c.id), "
            + "(select count(child) from Category child where child.parent.id = c.id)) "
            + "from Category c left join c.parent parent where lower(c.name) like lower(concat('%', :q, '%')) order by c.name, c.id")
    List<ManagementTaxonomyItem> findManagementItems(@Param("q") String query);
}
