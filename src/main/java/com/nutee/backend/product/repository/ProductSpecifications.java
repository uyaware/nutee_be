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
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.hibernate.query.criteria.JpaExpression;

/** Bộ lọc JPA cho sản phẩm, dùng subquery để phân trang không bị nhân bản bởi các quan hệ. */
public final class ProductSpecifications {
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
                predicates.add(search(root, query, cb, filter.q()));
            }
            if (filter.category() != null) {
                var categoryQuery = query.subquery(Integer.class);
                var product = categoryQuery.from(Product.class);
                var category = product.join("categories");
                categoryQuery.select(product.get("id")).where(cb.equal(product.get("id"), root.get("id")),
                        category.get("id").in(categoryIds));
                predicates.add(cb.exists(categoryQuery));
            }
            if (!filter.brands().isEmpty()) {
                predicates.add(root.get("brand").get("id").in(filter.brands()));
            }
            if (filter.minPrice() != null) {
                predicates.add(cb.ge(price, filter.minPrice()));
            }
            if (filter.maxPrice() != null) {
                predicates.add(cb.le(price, filter.maxPrice()));
            }
            if (filter.inStock()) {
                var stockQuery = query.subquery(Long.class);
                var variant = stockQuery.from(ProductVariant.class);
                stockQuery.select(cb.sumAsLong(variant.get("quantity"))).where(validVariant(variant, root, cb));
                predicates.add(cb.gt(stockQuery, 0L));
            }
            if (!filter.specifications().isEmpty()) {
                predicates.add(matchingVariant(root, query, cb, filter.specifications()));
            }
            if (query.getResultType() == Product.class) {
                applySort(root, query, cb, price, filter.sort());
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Dùng cùng điều kiện công khai cho chi tiết, biến thể và đánh giá. */
    public static Specification<Product> visibleById(int id) {
        return (root, query, cb) -> cb.and(visibility(root, query, cb), cb.equal(root.get("id"), id));
    }

    private static Predicate search(Root<Product> root, CriteriaQuery<?> query, CriteriaBuilder cb, String text) {
        String pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
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

        return cb.or(contains(root.get("name"), pattern, cb), contains(root.get("description"), pattern, cb),
                cb.exists(skuQuery), cb.exists(specificationQuery),
                contains(root.join("brand", JoinType.LEFT).get("name"), pattern, cb));
    }

    /** Tất cả thông số đã chọn phải cùng khớp một biến thể, không ghép cấu hình từ nhiều biến thể. */
    private static Predicate matchingVariant(Root<Product> root, CriteriaQuery<?> query, CriteriaBuilder cb,
            Map<String, List<String>> specifications) {
        var variantQuery = query.subquery(Integer.class);
        var variant = variantQuery.from(ProductVariant.class);
        var selected = new ArrayList<Predicate>();
        selected.add(validVariant(variant, root, cb));
        for (var entry : specifications.entrySet()) {
            var specificationQuery = variantQuery.subquery(Integer.class);
            var specification = specificationQuery.from(VariantSpecification.class);
            specificationQuery.select(specification.get("id")).where(
                    cb.equal(specification.get("variant").get("id"), variant.get("id")),
                    cb.equal(specification.get("specificationName"), entry.getKey()),
                    specification.get("specificationValue").in(entry.getValue()));
            selected.add(cb.exists(specificationQuery));
        }
        variantQuery.select(variant.get("id")).where(selected.toArray(Predicate[]::new));
        return cb.exists(variantQuery);
    }

    private static void applySort(Root<Product> root, CriteriaQuery<?> query, CriteriaBuilder cb,
            Expression<Integer> price, String sort) {
        switch (sort) {
            case "price-asc" -> query.orderBy(cb.asc(price), cb.asc(root.get("id")));
            case "price-desc" -> query.orderBy(cb.desc(price), cb.asc(root.get("id")));
            case "name" -> query.orderBy(cb.asc(cb.lower(root.get("name"))), cb.asc(root.get("id")));
            case "newest" -> query.orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
            default -> query.orderBy(cb.desc(cb.coalesce(root.get("soldCount"), 0)),
                    cb.desc(cb.coalesce(root.<BigDecimal>get("avgRating"), BigDecimal.ZERO)), cb.desc(root.get("id")));
        }
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
        Expression<Integer> original = cb.<Integer>coalesce().value(available).value(all);
        var promotion = root.join("promotion", JoinType.LEFT);
        var now = java.time.Instant.now();
        var eligible = cb.and(cb.isTrue(promotion.get("active")), cb.equal(promotion.get("scope"), "product"),
                cb.lessThanOrEqualTo(promotion.get("startsAt"), now), cb.greaterThan(promotion.get("endsAt"), now),
                cb.ge(promotion.get("value"), 1), cb.or(cb.equal(promotion.get("type"), "fixed"),
                        cb.and(cb.equal(promotion.get("type"), "percent"), cb.le(promotion.get("value"), 100))));
        // Promotion is monotonic for all variants of a product, so discounting the minimum preserves ordering.
        // Hibernate 6.6 Expression.as only changes the Java type; cast is needed before SQL multiplication.
        Expression<Long> base = ((JpaExpression<Integer>) original).cast(Long.class);
        Expression<Long> value = ((JpaExpression<Integer>) promotion.<Integer>get("value")).cast(Long.class);
        Expression<Long> percent = cb.quot(cb.prod(base, value), 100L).as(Long.class);
        Expression<Long> discount = cb.<Long>selectCase().when(cb.equal(promotion.get("type"), "percent"), percent)
                .otherwise(value);
        Expression<Long> reduced = cb.diff(base, discount);
        return cb.<Long>selectCase().when(eligible, cb.<Long>selectCase().when(cb.lt(reduced, 0L), 0L).otherwise(reduced))
                .otherwise(base).as(Integer.class);
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
