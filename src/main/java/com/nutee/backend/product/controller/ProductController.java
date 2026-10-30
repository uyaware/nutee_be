package com.nutee.backend.product.controller;

import com.nutee.backend.common.response.PageResponse;
import com.nutee.backend.product.dto.ProductFacetsResponse;
import com.nutee.backend.product.dto.ProductFilter;
import com.nutee.backend.product.dto.ProductResponse;
import com.nutee.backend.product.review.dto.ProductReviewResponse;
import com.nutee.backend.product.service.ProductService;
import com.nutee.backend.product.variant.dto.ProductVariantResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API đọc sản phẩm và các tài nguyên con, giữ nguyên URL hiện có. */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@SecurityRequirements
public class ProductController {
    private final ProductService service;

    @GetMapping
    public PageResponse<ProductResponse> products(@RequestParam MultiValueMap<String, String> query) {
        return service.list(ProductFilter.from(query));
    }

    @GetMapping("/{id}")
    public ProductResponse product(@PathVariable int id) {
        return service.get(id);
    }

    @GetMapping("/{id}/variants")
    public List<ProductVariantResponse> variants(@PathVariable int id) {
        return service.variants(id);
    }

    @GetMapping("/facets")
    public ProductFacetsResponse facets() {
        return service.facets();
    }

    @GetMapping("/{id}/reviews")
    public PageResponse<ProductReviewResponse> reviews(@PathVariable int id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return service.reviews(id, page, size);
    }
}
