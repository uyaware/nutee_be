package com.nutee.backend.product.image.repository;

import com.nutee.backend.product.image.entity.ProductImage;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository Spring Data JPA cho ảnh sản phẩm. */
public interface ProductImageRepository extends JpaRepository<ProductImage, Integer> {
    /** Tải ảnh theo lô và đặt ảnh chính trước, giữ thứ tự ID ổn định. */
    List<ProductImage> findByProduct_IdIn(List<Integer> ids, Sort sort);
}
