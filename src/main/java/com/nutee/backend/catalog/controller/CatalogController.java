package com.nutee.backend.catalog.controller;

import com.nutee.backend.catalog.dto.CatalogFilter;
import com.nutee.backend.catalog.dto.CatalogResponse.*;
import com.nutee.backend.catalog.service.CatalogService;
import com.nutee.backend.common.response.PageResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

/** API catalog công khai cho frontend, tất cả đường dẫn dùng /api/v1. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirements
public class CatalogController {
    private final CatalogService catalog;

    /** Danh sách sản phẩm có tìm kiếm, lọc và phân trang. */
    @GetMapping("/products")
    public PageResponse<Product> products(@RequestParam MultiValueMap<String, String> query) {
        return catalog.products(CatalogFilter.from(query));
    }

    /** Chi tiết gồm ảnh, thông số, brand, category và các variant. */
    @GetMapping("/products/{id}")
    public Product product(@PathVariable int id) { return catalog.product(id); }

    /** Biến thể của sản phẩm công khai. */
    @GetMapping("/products/{id}/variants")
    public List<Variant> variants(@PathVariable int id) { return catalog.product(id).variants(); }

    /** Giá trị dựng bộ lọc được lấy từ database. */
    @GetMapping("/products/facets")
    public Facets facets() { return catalog.facets(); }

    /** Đánh giá của sản phẩm, có phân trang. */
    @GetMapping("/products/{id}/reviews")
    public PageResponse<Review> reviews(@PathVariable int id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) { return catalog.reviews(id, page, size); }

    /** Danh sách thương hiệu. */
    @GetMapping("/brands")
    public List<Brand> brands() { return catalog.brands(); }

    /** Chi tiết thương hiệu. */
    @GetMapping("/brands/{id}")
    public Brand brand(@PathVariable int id) { return catalog.brand(id); }

    /** Danh sách danh mục với parentId. */
    @GetMapping("/categories")
    public List<Category> categories() { return catalog.categories(); }

    /** Chi tiết danh mục. */
    @GetMapping("/categories/{id}")
    public Category category(@PathVariable int id) { return catalog.category(id); }
}
