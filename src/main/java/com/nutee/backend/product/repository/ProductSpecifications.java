package com.nutee.backend.product.repository;

import com.nutee.backend.product.dto.ProductFilter;
import com.nutee.backend.product.entity.Product;
import com.nutee.backend.product.variant.entity.ProductVariant;
import com.nutee.backend.product.variant.specification.entity.VariantSpecification;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

/** Bộ lọc JPA cho sản phẩm, dùng subquery để phân trang không bị nhân bản bởi các quan hệ. */
public final class ProductSpecifications {
    /** Không khởi tạo lớp chứa các Specification. */
    private ProductSpecifications() {}

    /** Áp dụng tìm kiếm, bộ lọc và thứ tự trên database, không lọc kết quả sau phân trang. */
    public static Specification<Product> matching(ProductFilter filter, Set<Integer> categoryIds) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(visibility(root, query, cb));
            Expression<Integer> price = null;
            if (filter.minPrice() != null || filter.maxPrice() != null || filter.sort().startsWith("price-")) {
                price = price(root, query, cb);
            }
            if (!filter.q().isBlank()) {
                String pattern = "%" + filter.q().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                var skuQuery = query.subquery(Integer.class);
                var skuVariant = skuQuery.from(ProductVariant.class);
                skuQuery.select(skuVariant.get("id")).where(
                        cb.equal(skuVariant.get("product").get("id"), root.get("id")),
                        contains(skuVariant.get("sku"), pattern, cb));
                var specificationQuery = query.subquery(Integer.class);
                var specification = specificationQuery.from(VariantSpecification.class);
                var variant = specification.join("variant");
                specificationQuery.select(specification.get("id")).where(
                        cb.equal(variant.get("product").get("id"), root.get("id")),
                        cb.ge(variant.get("price"), 0), cb.ge(variant.get("quantity"), 0),
                        contains(specification.get("specificationValue"), pattern, cb));
                predicates.add(cb.or(contains(root.get("name"), pattern, cb),
                        contains(root.get("description"), pattern, cb), cb.exists(skuQuery),
                        cb.exists(specificationQuery), contains(root.join("brand", JoinType.LEFT).get("name"), pattern, cb)));
            }
            if (filter.category() != null) {
                var categoryQuery = query.subquery(Integer.class);
                var product = categoryQuery.from(Product.class);
                var category = product.join("categories");
                categoryQuery.select(product.get("id")).where(cb.equal(product.get("id"), root.get("id")),
                        category.get("id").in(categoryIds));
                predicates.add(cb.exists(categoryQuery));
            }
            if (!filter.brands().isEmpty()) predicates.add(root.get("brand").get("id").in(filter.brands()));
            if (filter.minPrice() != null) predicates.add(cb.ge(price, filter.minPrice()));
            if (filter.maxPrice() != null) predicates.add(cb.le(price, filter.maxPrice()));
            if (filter.inStock()) {
                var stockQuery = query.subquery(Long.class);
                var variant = stockQuery.from(ProductVariant.class);
                stockQuery.select(cb.sumAsLong(variant.get("quantity"))).where(validVariant(variant, root, cb));
                predicates.add(cb.gt(stockQuery, 0L));
            }
            if (!filter.specifications().isEmpty()) {
                var variantQuery = query.subquery(Integer.class);
                var variant = variantQuery.from(ProductVariant.class);
                var selected = new ArrayList<Predicate>();
                selected.add(validVariant(variant, root, cb));
                for (var entry : filter.specifications().entrySet()) {
                    var specificationQuery = variantQuery.subquery(Integer.class);
                    var specification = specificationQuery.from(VariantSpecification.class);
                    specificationQuery.select(specification.get("id")).where(
                            cb.equal(specification.get("variant").get("id"), variant.get("id")),
                            cb.equal(specification.get("specificationName"), entry.getKey()),
                            specification.get("specificationValue").in(entry.getValue()));
                    selected.add(cb.exists(specificationQuery));
                }
                variantQuery.select(variant.get("id")).where(selected.toArray(Predicate[]::new));
                predicates.add(cb.exists(variantQuery));
            }
            if (query.getResultType() == Product.class) {
                switch (filter.sort()) {
                    case "price-asc" -> query.orderBy(cb.asc(price), cb.asc(root.get("id")));
                    case "price-desc" -> query.orderBy(cb.desc(price), cb.asc(root.get("id")));
                    case "name" -> query.orderBy(cb.asc(cb.lower(root.get("name"))), cb.asc(root.get("id")));
                    case "newest" -> query.orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
                    default -> query.orderBy(cb.desc(cb.coalesce(root.get("soldCount"), 0)),
                            cb.desc(cb.coalesce(root.<BigDecimal>get("avgRating"), BigDecimal.ZERO)), cb.desc(root.get("id")));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Dùng cùng điều kiện công khai cho chi tiết, biến thể và đánh giá. */
    public static Specification<Product> visibleById(int id) {
        return (root, query, cb) -> cb.and(visibility(root, query, cb), cb.equal(root.get("id"), id));
    }

    /** Chỉ công khai sản phẩm đã xuất bản và có ít nhất một biến thể hợp lệ. */
    private static Predicate visibility(Root<Product> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        var variants = query.subquery(Integer.class);
        var variant = variants.from(ProductVariant.class);
        variants.select(variant.get("id")).where(validVariant(variant, root, cb));
        return cb.and(cb.isTrue(root.get("published")), cb.exists(variants));
    }

    /** Giá hiển thị ưu tiên biến thể còn hàng, sau đó mới lấy giá thấp nhất toàn bộ. */
    private static Expression<Integer> price(Root<Product> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        var available = query.subquery(Integer.class);
        var availableVariant = available.from(ProductVariant.class);
        available.select(cb.min(availableVariant.<Integer>get("price"))).where(
                validVariant(availableVariant, root, cb), cb.gt(availableVariant.get("quantity"), 0));
        var all = query.subquery(Integer.class);
        var variant = all.from(ProductVariant.class);
        all.select(cb.min(variant.<Integer>get("price"))).where(validVariant(variant, root, cb));
        return cb.<Integer>coalesce().value(available).value(all);
    }

    /** Điều kiện biến thể có giá và số lượng được biết, không âm, thuộc đúng sản phẩm. */
    private static Predicate validVariant(Root<ProductVariant> variant, Root<Product> product, CriteriaBuilder cb) {
        return cb.and(cb.equal(variant.get("product").get("id"), product.get("id")),
                cb.ge(variant.get("price"), 0), cb.ge(variant.get("quantity"), 0));
    }

    /** Tìm chuỗi nguyên văn không phân biệt hoa thường; wildcard đã được escape. */
    private static Predicate contains(Expression<String> text, String pattern, CriteriaBuilder cb) {
        return cb.like(cb.lower(text), cb.lower(cb.literal(pattern)), '\\');
    }
}
