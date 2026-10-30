package com.nutee.backend.product.service;

import com.nutee.backend.brand.dto.BrandResponse;
import com.nutee.backend.brand.service.BrandService;
import com.nutee.backend.category.dto.CategoryResponse;
import com.nutee.backend.category.entity.Category;
import com.nutee.backend.category.service.CategoryService;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.common.response.PageResponse;
import com.nutee.backend.product.dto.ProductFacetsResponse;
import com.nutee.backend.product.dto.ProductFilter;
import com.nutee.backend.product.dto.ProductResponse;
import com.nutee.backend.product.entity.Product;
import com.nutee.backend.product.image.dto.ProductImageResponse;
import com.nutee.backend.product.image.repository.ProductImageRepository;
import com.nutee.backend.product.repository.ProductRepository;
import com.nutee.backend.product.repository.ProductSpecifications;
import com.nutee.backend.product.review.dto.ProductReviewResponse;
import com.nutee.backend.product.review.repository.ProductReviewRepository;
import com.nutee.backend.product.variant.dto.ProductVariantResponse;
import com.nutee.backend.product.variant.entity.ProductVariant;
import com.nutee.backend.product.variant.repository.ProductVariantRepository;
import com.nutee.backend.product.variant.specification.dto.SpecificationFacetResponse;
import com.nutee.backend.product.variant.specification.dto.VariantSpecificationResponse;
import com.nutee.backend.product.variant.specification.repository.VariantSpecificationRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đọc sản phẩm bằng Spring Data JPA, giữ phân trang database và tải quan hệ theo lô. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository products;
    private final BrandService brands;
    private final CategoryService categories;
    private final ProductVariantRepository variants;
    private final ProductImageRepository images;
    private final VariantSpecificationRepository specifications;
    private final ProductReviewRepository reviews;

    /** Đếm để giới hạn trang, sau đó dùng Slice để không chạy lại count query. */
    public PageResponse<ProductResponse> list(ProductFilter filter) {
        var categoryIds = filter.category() == null ? Set.<Integer>of() : categories.descendantIds(filter.category());
        var specification = ProductSpecifications.matching(filter, categoryIds);
        long total = products.count(specification);
        int page = (int) Math.min(filter.page(), Math.max(0, (total - 1) / filter.size()));
        var request = PageRequest.of(page, filter.size());
        List<Product> rows = total == 0 ? List.of()
                : products.findBy(specification, query -> query.slice(request).getContent());
        return PageResponse.from(new PageImpl<>(assemble(rows), request, total));
    }

    /** Trả sản phẩm công khai hoặc lỗi tương ứng khi ID sai/không tồn tại. */
    public ProductResponse get(int id) {
        validateId(id);
        var product = products.findOne(ProductSpecifications.visibleById(id))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found."));
        return assemble(List.of(product)).getFirst();
    }

    /** Đọc biến thể mà không tải danh mục, thương hiệu hay toàn bộ ảnh của sản phẩm. */
    public List<ProductVariantResponse> variants(int id) {
        requireVisible(id);
        return loadVariants(List.of(id)).getOrDefault(id, List.of());
    }

    /** Ghép facets từ thông số và danh sách thương hiệu/danh mục; không trả entity qua API. */
    public ProductFacetsResponse facets() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        specifications.findFacetValues().forEach(value ->
                values.computeIfAbsent(value.name(), key -> new ArrayList<>()).add(value.value()));
        return new ProductFacetsResponse(brands.list(), categories.list(), values.entrySet().stream()
                .map(entry -> new SpecificationFacetResponse(entry.getKey(), entry.getValue())).toList());
    }

    /** Phân trang bản sửa đánh giá cuối cùng, kể cả khi nhiều bản có cùng thời điểm. */
    public PageResponse<ProductReviewResponse> reviews(int id, int page, int size) {
        requireVisible(id);
        if (page < 0 || size < 1 || size > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid review pagination.");
        long total = reviews.countByOrderItem_Variant_Product_IdAndHistoriesIsNotEmpty(id);
        int current = (int) Math.min(page, Math.max(0, (total - 1) / size));
        var request = PageRequest.of(current, size);
        return PageResponse.from(new PageImpl<>(reviews.findLatestByProductId(id, request), request, total));
    }

    /** Kiểm tra định dạng ID như hợp đồng API hiện có. */
    private void validateId(int id) {
        if (id < 1) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid product ID.");
    }

    /** Áp dụng cùng điều kiện công khai cho các tài nguyên con. */
    private void requireVisible(int id) {
        validateId(id);
        if (!products.exists(ProductSpecifications.visibleById(id))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Product not found.");
        }
    }

    /** Ghép dữ liệu sau phân trang; fetch join collection chỉ chạy cho danh sách ID đã chọn. */
    private List<ProductResponse> assemble(List<Product> rows) {
        if (rows.isEmpty()) return List.of();
        var ids = rows.stream().map(Product::getId).toList();
        var related = products.findDistinctByIdIn(ids).stream().collect(Collectors.toMap(Product::getId, value -> value));
        var groupedVariants = loadVariants(ids);
        Map<Integer, List<ProductImageResponse>> groupedImages = new HashMap<>();
        for (var image : images.findByProduct_IdIn(ids, Sort.by(Sort.Order.desc("primaryImage"), Sort.Order.asc("id")))) {
            groupedImages.computeIfAbsent(image.getProduct().getId(), key -> new ArrayList<>())
                    .add(new ProductImageResponse(image.getId(), image.getImageUrl(), image.isPrimaryImage()));
        }
        var counts = reviews.countByProductIds(ids).stream()
                .collect(Collectors.toMap(ProductReviewRepository.ProductCount::getProductId,
                        ProductReviewRepository.ProductCount::getReviewCount));
        return rows.stream().map(row -> response(related.get(row.getId()),
                groupedVariants.getOrDefault(row.getId(), List.of()),
                groupedImages.getOrDefault(row.getId(), List.of()), counts.getOrDefault(row.getId(), 0L))).toList();
    }

    /** Tải biến thể và thông số bằng hai truy vấn thay vì truy vấn riêng từng biến thể. */
    private Map<Integer, List<ProductVariantResponse>> loadVariants(List<Integer> ids) {
        var rows = variants.findByProduct_IdInAndPriceGreaterThanEqualAndQuantityGreaterThanEqual(
                ids, 0, 0, Sort.by("price", "id"));
        var variantIds = rows.stream().map(ProductVariant::getId).toList();
        Map<Integer, List<VariantSpecificationResponse>> groupedSpecifications = new HashMap<>();
        if (!variantIds.isEmpty()) {
            for (var specification : specifications.findByVariant_IdIn(
                    variantIds, Sort.by("specificationName", "specificationValue", "variant.id"))) {
                groupedSpecifications.computeIfAbsent(specification.getVariant().getId(), key -> new ArrayList<>())
                        .add(new VariantSpecificationResponse(specification.getSpecificationName(), specification.getSpecificationValue()));
            }
        }
        Map<Integer, List<ProductVariantResponse>> result = new HashMap<>();
        for (var variant : rows) {
            result.computeIfAbsent(variant.getProduct().getId(), key -> new ArrayList<>()).add(new ProductVariantResponse(
                    variant.getId(), variant.getSku(), variant.getPrice(), variant.getQuantity(),
                    variant.getImage() == null ? null : variant.getImage().getId(),
                    groupedSpecifications.getOrDefault(variant.getId(), List.of())));
        }
        return result;
    }

    /** Tạo DTO từ entity đã tải quan hệ, giữ nguyên giá/stock và thứ tự JSON trước khi chuyển JPA. */
    private ProductResponse response(Product product, List<ProductVariantResponse> variants,
            List<ProductImageResponse> images, long reviewCount) {
        int minimum = variants.stream().mapToInt(ProductVariantResponse::price).min().orElseThrow();
        int price = variants.stream().filter(variant -> variant.quantity() > 0)
                .mapToInt(ProductVariantResponse::price).min().orElse(minimum);
        int maximum = variants.stream().mapToInt(ProductVariantResponse::price).max().orElseThrow();
        long stock = variants.stream().mapToLong(ProductVariantResponse::quantity).sum();
        var productSpecifications = variants.stream().flatMap(variant -> variant.specifications().stream()).distinct()
                .sorted(Comparator.comparing(VariantSpecificationResponse::name).thenComparing(VariantSpecificationResponse::value)).toList();
        var brand = product.getBrand();
        var productBrands = brand == null ? List.<BrandResponse>of() : List.of(new BrandResponse(brand.getId(), brand.getName()));
        var productCategories = product.getCategories().stream().sorted(Comparator.comparing(Category::getId))
                .map(category -> new CategoryResponse(category.getId(), category.getName(),
                        category.getParent() == null ? null : category.getParent().getId())).toList();
        return new ProductResponse(product.getId(), product.getDefaultVariantId(), product.getName(), product.getDescription(),
                product.getAvgRating() == null ? BigDecimal.ZERO : product.getAvgRating(),
                product.getSoldCount() == null ? 0 : product.getSoldCount(), Math.toIntExact(reviewCount), price, maximum, stock,
                product.getUpdatedAt(), productBrands, productCategories, productSpecifications, variants, images);
    }
}
