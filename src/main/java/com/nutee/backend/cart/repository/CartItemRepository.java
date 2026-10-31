package com.nutee.backend.cart.repository;

import com.nutee.backend.cart.entity.CartItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy cập giỏ luôn kèm ID tài khoản, không tra dòng giỏ chỉ theo ID từ client. */
public interface CartItemRepository extends JpaRepository<CartItem, Integer> {
    @EntityGraph(attributePaths = {"variant", "variant.product", "variant.product.promotion", "variant.image"})
    List<CartItem> findByUser_IdOrderByIdAsc(UUID userId);

    Optional<CartItem> findByUser_IdAndVariant_Id(UUID userId, int variantId);

    void deleteByUser_Id(UUID userId);
}
