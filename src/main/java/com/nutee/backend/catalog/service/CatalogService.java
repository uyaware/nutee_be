package com.nutee.backend.catalog.service;

import com.nutee.backend.catalog.dto.CatalogFilter;
import com.nutee.backend.catalog.dto.CatalogResponse.*;
import com.nutee.backend.catalog.repository.CatalogRepository;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.common.response.PageResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ đọc catalog, không công khai sản phẩm chưa xuất bản. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {
    private final CatalogRepository repository;

    /** Phân trang ổn định; yêu cầu vượt trang hiện có được đưa về trang cuối. */
    public PageResponse<Product> products(CatalogFilter filter) {
        long total = repository.count(filter);
        int page = (int) Math.min(filter.page(), Math.max(0, (total - 1) / filter.size()));
        return PageResponse.from(new PageImpl<>(repository.products(filter, page), PageRequest.of(page, filter.size()), total));
    }

    /** Trả chi tiết công khai hoặc 404, không để lộ thông tin sản phẩm ẩn. */
    public Product product(int id) {
        if (id < 1) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid product ID.");
        return repository.product(id).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found."));
    }

    /** Thương hiệu từ database để dựng bộ lọc và điều hướng. */
    public List<Brand> brands() { return repository.brands(); }

    /** Tìm thương hiệu theo ID. */
    public Brand brand(int id) {
        return brands().stream().filter(brand -> brand.id() == id).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Brand not found."));
    }

    /** Danh sách danh mục có quan hệ cha/con. */
    public List<Category> categories() { return repository.categories(); }

    /** Tìm danh mục theo ID. */
    public Category category(int id) {
        return categories().stream().filter(category -> category.id() == id).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found."));
    }

    /** Bộ giá trị lọc lấy từ dữ liệu thật. */
    public Facets facets() { return new Facets(brands(), categories(), repository.specificationFacets()); }

    /** Phân trang bản sửa mới nhất của các review thuộc sản phẩm công khai. */
    public PageResponse<Review> reviews(int id, int page, int size) {
        product(id);
        if (page < 0 || size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid review pagination.");
        long total = repository.reviewCount(id);
        int current = (int) Math.min(page, Math.max(0, (total - 1) / size));
        return PageResponse.from(new PageImpl<>(repository.reviews(id, current, size), PageRequest.of(current, size), total));
    }
}
