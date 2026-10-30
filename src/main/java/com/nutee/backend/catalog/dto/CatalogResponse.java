package com.nutee.backend.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** DTO catalog theo đúng quan hệ sản phẩm/biến thể của database. */
public final class CatalogResponse {
    /** Không tạo instance của lớp chứa DTO. */
    private CatalogResponse() {}

    /** Thương hiệu công khai. */
    public record Brand(int id, String name) {}
    /** Danh mục với parentId null khi là gốc. */
    public record Category(int id, Integer parentId, String name) {}
    /** Giá trị thông số biến thể, được tổng hợp khi đọc sản phẩm. */
    public record Specification(String name, String value) {}
    /** Biến thể có SKU, giá và tồn kho riêng theo schema hiện có. */
    public record Variant(int id, String sku, int price, int quantity) {}
    /** Ảnh sản phẩm và cờ ảnh chính. */
    public record Image(int id, String url, boolean primary) {}
    /** Sản phẩm cùng quan hệ được tải theo lô. */
    public record Product(int id, String name, String description, BigDecimal avgRating, int soldCount,
            int reviewCount, int price, int maxPrice, long stock, Instant updatedAt,
            List<Brand> brands, List<Category> categories, List<Specification> specifications,
            List<Variant> variants, List<Image> images) {}
    /** Các giá trị có sẵn của một thông số. */
    public record SpecificationFacet(String name, List<String> values) {}
    /** Dữ liệu dựng bộ lọc từ database. */
    public record Facets(List<Brand> brands, List<Category> categories, List<SpecificationFacet> specifications) {}
    /** Bản sửa mới nhất của mỗi review, không công khai email hoặc thông tin đăng nhập. */
    public record Review(int id, String authorName, int rating, String comment, Instant createdAt) {}
}
