package com.nutee.backend.cart.service;

import com.nutee.backend.cart.dto.CartItemResponse;
import com.nutee.backend.cart.dto.CartItemStatus;
import com.nutee.backend.cart.dto.CartResponse;
import com.nutee.backend.cart.entity.CartItem;
import com.nutee.backend.cart.repository.CartItemRepository;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.product.image.repository.ProductImageRepository;
import com.nutee.backend.product.variant.entity.ProductVariant;
import com.nutee.backend.product.variant.repository.ProductVariantRepository;
import com.nutee.backend.product.variant.specification.dto.VariantSpecificationResponse;
import com.nutee.backend.product.variant.specification.repository.VariantSpecificationRepository;
import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quản lý giỏ theo biến thể, kiểm tra tồn kho hiện tại và tuần tự hóa thao tác ghi của mỗi tài khoản. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {
    private final CartItemRepository items;
    private final UserRepository users;
    private final ProductVariantRepository variants;
    private final ProductImageRepository images;
    private final VariantSpecificationRepository specifications;

    public CartResponse get(UUID userId) {
        requireActive(users.findById(userId));
        return response(userId);
    }

    /** Cộng số lượng trong transaction; khóa tài khoản bảo vệ cả trường hợp dòng giỏ chưa tồn tại. */
    @Transactional
    public CartResponse add(UUID userId, int variantId, int quantity) {
        User user = requireActive(users.findLockedById(userId));
        ProductVariant variant = variants.findById(variantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy biến thể sản phẩm."));
        var existing = items.findByUser_IdAndVariant_Id(userId, variantId);
        int total = (existing.isEmpty() ? 0 : existing.get().getQuantity()) + quantity;
        validateQuantity(variant, total);
        if (existing.isPresent()) {
            existing.get().changeQuantity(total);
        } else {
            items.save(new CartItem(user, variant, total));
        }
        return response(userId);
    }

    /** PUT đặt số lượng tuyệt đối; các request cùng tài khoản thực hiện theo thứ tự khóa dòng. */
    @Transactional
    public CartResponse setQuantity(UUID userId, int variantId, int quantity) {
        requireActive(users.findLockedById(userId));
        CartItem item = items.findByUser_IdAndVariant_Id(userId, variantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sản phẩm không có trong giỏ hàng."));
        validateQuantity(item.getVariant(), quantity);
        item.changeQuantity(quantity);
        return response(userId);
    }

    /** Xóa lại dòng đã vắng vẫn thành công, kể cả sản phẩm đã ngừng bán. */
    @Transactional
    public CartResponse remove(UUID userId, int variantId) {
        requireActive(users.findLockedById(userId));
        items.findByUser_IdAndVariant_Id(userId, variantId).ifPresent(items::delete);
        return response(userId);
    }

    @Transactional
    public CartResponse clear(UUID userId) {
        requireActive(users.findLockedById(userId));
        items.deleteByUser_Id(userId);
        return response(userId);
    }

    private User requireActive(Optional<User> account) {
        User user = account.orElseThrow(() -> new BadCredentialsException("Invalid account."));
        if (!user.isActive()) {
            throw new BadCredentialsException("Invalid account.");
        }
        return user;
    }

    private void validateQuantity(ProductVariant variant, int quantity) {
        if (!Boolean.TRUE.equals(variant.getProduct().getPublished()) || variant.getPrice() == null
                || variant.getPrice() < 0 || variant.getQuantity() == null || variant.getQuantity() < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Biến thể này hiện không thể mua.");
        }
        if (quantity < 1 || quantity > CartItem.MAX_QUANTITY || quantity > variant.getQuantity()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Số lượng vượt giới hạn hoặc tồn kho hiện tại. Tối đa "
                            + Math.min(CartItem.MAX_QUANTITY, variant.getQuantity()) + " sản phẩm.");
        }
    }

    private CartResponse response(UUID userId) {
        var rows = items.findByUser_IdOrderByIdAsc(userId);
        if (rows.isEmpty()) {
            return new CartResponse(userId, List.of(), 0, 0, false);
        }
        var productIds = rows.stream().map(item -> item.getVariant().getProduct().getId()).distinct().toList();
        Map<Integer, String> fallbackImages = new HashMap<>();
        for (var image : images.findByProduct_IdIn(productIds,
                Sort.by(Sort.Order.desc("primaryImage"), Sort.Order.asc("id")))) {
            fallbackImages.putIfAbsent(image.getProduct().getId(), image.getImageUrl());
        }
        var variantIds = rows.stream().map(item -> item.getVariant().getId()).toList();
        Map<Integer, List<VariantSpecificationResponse>> groupedSpecifications = new HashMap<>();
        for (var specification : specifications.findByVariant_IdIn(variantIds,
                Sort.by("specificationName", "specificationValue"))) {
            groupedSpecifications.computeIfAbsent(specification.getVariant().getId(), key -> new ArrayList<>())
                    .add(new VariantSpecificationResponse(specification.getSpecificationName(), specification.getSpecificationValue()));
        }
        var result = rows.stream().map(item -> itemResponse(item, fallbackImages, groupedSpecifications)).toList();
        long quantity = result.stream().mapToLong(CartItemResponse::quantity).sum();
        long subtotal = result.stream().filter(item -> item.status() == CartItemStatus.AVAILABLE)
                .mapToLong(CartItemResponse::lineTotal).sum();
        boolean hasIssues = result.stream().anyMatch(item -> item.status() != CartItemStatus.AVAILABLE);
        return new CartResponse(userId, result, quantity, subtotal, hasIssues);
    }

    private CartItemResponse itemResponse(CartItem item, Map<Integer, String> fallbackImages,
            Map<Integer, List<VariantSpecificationResponse>> groupedSpecifications) {
        var variant = item.getVariant();
        var product = variant.getProduct();
        var image = variant.getImage();
        Integer price = variant.getPrice();
        int stock = variant.getQuantity() == null ? 0 : Math.max(0, variant.getQuantity());
        String imageUrl = image == null ? fallbackImages.getOrDefault(product.getId(), "") : image.getImageUrl();
        CartItemStatus status;
        if (!Boolean.TRUE.equals(product.getPublished()) || price == null || price < 0 || variant.getQuantity() == null) {
            status = CartItemStatus.HIDDEN;
        } else if (stock == 0) {
            status = CartItemStatus.OUT_OF_STOCK;
        } else if (item.getQuantity() > stock || item.getQuantity() > CartItem.MAX_QUANTITY) {
            status = CartItemStatus.INSUFFICIENT_STOCK;
        } else {
            status = CartItemStatus.AVAILABLE;
        }
        Long lineTotal = price == null ? null : (long) price * item.getQuantity();
        return new CartItemResponse(variant.getId(), product.getId(), variant.getSku(), product.getName(), imageUrl,
                price, item.getQuantity(), stock, status, lineTotal,
                groupedSpecifications.getOrDefault(variant.getId(), List.of()));
    }
}
