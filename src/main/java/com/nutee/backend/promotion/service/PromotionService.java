package com.nutee.backend.promotion.service;

import com.nutee.backend.common.discount.DiscountFilters;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.common.response.PageResponse;
import com.nutee.backend.product.entity.Product;
import com.nutee.backend.product.repository.ProductRepository;
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
        return PageResponse.from(result.map(product -> new ProductOption(product.getId(), product.getName())));
    }

    @Transactional
    public PromotionResponse create(PromotionRequest request) {
        Promotion rule = rules.saveAndFlush(new Promotion(request));
        assign(rule, request.productIds());
        return response(rule);
    }

    @Transactional
    public PromotionResponse update(UUID id, PromotionRequest request) {
        Promotion rule = locked(id);
        rule.update(request);
        assign(rule, request.productIds());
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
                throw new ApiException(HttpStatus.CONFLICT, "Sản phẩm đã thuộc promotion khác. Hãy gỡ liên kết cũ trước.");
            product.assignPromotion(ids.contains(product.getId()) ? rule : null);
        }
        products.flush(); rules.flush();
    }

    private Promotion require(UUID id) { return rules.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy promotion.")); }
    private Promotion locked(UUID id) { return rules.findLockedById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy promotion.")); }
    private PromotionResponse response(Promotion rule) { return PromotionResponse.from(rule, groupedProducts(List.of(rule.getId())).getOrDefault(rule.getId(), List.of())); }
    private Map<UUID, List<ProductOption>> groupedProducts(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return products.findByPromotion_IdIn(ids).stream().sorted(Comparator.comparing(Product::getId)).collect(Collectors.groupingBy(
                product -> product.getPromotion().getId(), Collectors.mapping(product -> new ProductOption(product.getId(), product.getName()), Collectors.toList())));
    }
}
