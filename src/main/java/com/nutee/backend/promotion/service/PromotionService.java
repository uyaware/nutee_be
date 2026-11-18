package com.nutee.backend.promotion.service;

import com.nutee.backend.common.discount.DiscountFilters;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.common.response.PageResponse;
import com.nutee.backend.product.entity.Product;
import com.nutee.backend.product.repository.ProductRepository;
import com.nutee.backend.product.image.entity.ProductImage;
import com.nutee.backend.product.image.repository.ProductImageRepository;
import com.nutee.backend.product.variant.entity.ProductVariant;
import com.nutee.backend.product.variant.repository.ProductVariantRepository;
import com.nutee.backend.promotion.dto.PromotionRequest;
import com.nutee.backend.promotion.dto.PromotionResponse;
import com.nutee.backend.promotion.dto.PromotionResponse.ProductOption;
import com.nutee.backend.promotion.entity.Promotion;
import com.nutee.backend.promotion.repository.PromotionRepository;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quản trị promotion theo sản phẩm, không tự lấy sản phẩm đang thuộc promotion khác. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionService {
    private final PromotionRepository rules;
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final ProductVariantRepository variants;

    public PageResponse<PromotionResponse> list(String q, String status, int page, int size) {
        var result = rules.findAll(DiscountFilters.matching(q, status), PageRequest.of(page, size, Sort.by("code", "id")));
        var grouped = groupedProducts(result.getContent().stream().map(Promotion::getId).toList());
        return PageResponse.from(result.map(rule -> PromotionResponse.from(rule, grouped.getOrDefault(rule.getId(), List.of()))));
    }

    public PromotionResponse get(UUID id) { return response(require(id)); }

    public PageResponse<ProductOption> products(String q, int page, int size) {
        if (q.length() > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "Từ khóa quá dài.");
        String text = q.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        var result = products.findAll((root, query, cb) -> cb.like(cb.lower(root.get("name")), "%" + text + "%", '\\'),
                PageRequest.of(page, size, Sort.by("id")));
        var details = productOptions(result.getContent());
        return PageResponse.from(result.map(product -> details.get(product.getId())));
    }

    @Transactional
    public PromotionResponse create(PromotionRequest request) {
        Promotion rule = rules.saveAndFlush(new Promotion(request));
        assign(rule, requestedProducts(request));
        return response(rule);
    }

    @Transactional
    public PromotionResponse update(UUID id, PromotionRequest request) {
        Promotion rule = locked(id);
        rule.update(request);
        assign(rule, requestedProducts(request));
        return response(rule);
    }

    @Transactional
    public PromotionResponse active(UUID id, boolean enabled) {
        var rule = locked(id); rule.setActive(enabled); return response(rule);
    }

    /** Gỡ liên kết sản phẩm trước khi xóa rule; không xóa sản phẩm hoặc giá gốc. */
    @Transactional
    public void delete(UUID id) {
        var rule = locked(id);
        var ids = products.findByPromotion_IdIn(List.of(id)).stream().map(Product::getId).toList();
        if (!ids.isEmpty()) products.findLockedByIdIn(ids, Sort.by("id")).forEach(product -> product.assignPromotion(null));
        products.flush(); rules.delete(rule); rules.flush();
    }

    private void assign(Promotion rule, List<Integer> requested) {
        Set<Integer> ids = new HashSet<>(requested);
        if (ids.size() != requested.size()) throw new ApiException(HttpStatus.BAD_REQUEST, "Sản phẩm bị lặp.");
        var current = products.findByPromotion_IdIn(List.of(rule.getId())).stream().map(Product::getId).toList();
        Set<Integer> all = new HashSet<>(current); all.addAll(ids);
        var rows = products.findLockedByIdIn(new ArrayList<>(all), Sort.by("id"));
        if (rows.size() != all.size()) throw new ApiException(HttpStatus.BAD_REQUEST, "Có sản phẩm không tồn tại.");
        for (var product : rows) {
            if (ids.contains(product.getId()) && product.getPromotion() != null && !product.getPromotion().getId().equals(rule.getId()))
                throw new ApiException(HttpStatus.CONFLICT, "Sản phẩm " + product.getName() + " (#" + product.getId() + ") đã thuộc promotion " + product.getPromotion().getCode() + ". Hãy gỡ liên kết cũ trước.");
        }
        for (var product : rows) {
            product.assignPromotion(ids.contains(product.getId()) ? rule : null);
        }
        products.flush(); rules.flush();
    }

    private List<Integer> requestedProducts(PromotionRequest request) {
        return "all".equals(request.scope())
                ? products.findAllProductIds()
                : request.productIds();
    }

    private Promotion require(UUID id) { return rules.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy promotion.")); }
    private Promotion locked(UUID id) { return rules.findLockedById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy promotion.")); }
    private PromotionResponse response(Promotion rule) { return PromotionResponse.from(rule, groupedProducts(List.of(rule.getId())).getOrDefault(rule.getId(), List.of())); }
    private Map<UUID, List<ProductOption>> groupedProducts(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        var rows = products.findByPromotion_IdIn(ids).stream().sorted(Comparator.comparing(Product::getId)).toList();
        var options = productOptions(rows);
        return rows.stream().collect(Collectors.groupingBy(product -> product.getPromotion().getId(),
                Collectors.mapping(product -> options.get(product.getId()), Collectors.toList())));
    }

    private Map<Integer, ProductOption> productOptions(List<Product> rows) {
        if (rows.isEmpty()) return Map.of();
        var ids = rows.stream().map(Product::getId).toList();
        var related = products.findDistinctByIdIn(ids).stream().collect(Collectors.toMap(Product::getId, product -> product));
        var groupedImages = images.findByProduct_IdIn(ids, Sort.by(Sort.Order.desc("primaryImage"), Sort.Order.asc("id")))
                .stream().collect(Collectors.groupingBy(image -> image.getProduct().getId()));
        var groupedVariants = variants.findByProduct_IdInAndPriceGreaterThanEqualAndQuantityGreaterThanEqual(ids, 0, 0, Sort.by("id"))
                .stream().collect(Collectors.groupingBy(variant -> variant.getProduct().getId()));
        return rows.stream().collect(Collectors.toMap(Product::getId, product -> {
            var prices = groupedVariants.getOrDefault(product.getId(), List.of()).stream().map(ProductVariant::getPrice).toList();
            var image = groupedImages.getOrDefault(product.getId(), List.of()).stream().findFirst().map(ProductImage::getImageUrl).orElse(null);
            var promotion = related.get(product.getId()).getPromotion();
            return new ProductOption(product.getId(), product.getName(), Boolean.TRUE.equals(product.getPublished()), image,
                    prices.stream().min(Integer::compareTo).orElse(null), prices.stream().max(Integer::compareTo).orElse(null),
                    promotion == null ? null : promotion.getId(), promotion == null ? null : promotion.getCode());
        }));
    }
}
