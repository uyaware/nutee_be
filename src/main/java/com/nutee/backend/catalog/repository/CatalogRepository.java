package com.nutee.backend.catalog.repository;

import com.nutee.backend.catalog.dto.CatalogFilter;
import com.nutee.backend.catalog.dto.CatalogResponse.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Đọc các bảng catalog hiện có bằng SQL có tham số; quan hệ được tải theo lô. */
@Repository
@RequiredArgsConstructor
public class CatalogRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String SOURCE = """
            FROM products p
            JOIN LATERAL (
              SELECT coalesce(min(v.price) FILTER (WHERE v.quantity > 0), min(v.price)) AS price,
                     max(v.price) AS max_price, coalesce(sum(v.quantity), 0) AS stock
              FROM product_variants v WHERE v.product_id = p.id AND v.price >= 0 AND v.quantity >= 0
            ) offer ON offer.price IS NOT NULL
            """;

    /** Các cột nền trước khi ghép các quan hệ của sản phẩm. */
    private record ProductRow(int id, Integer defaultVariantId, String name, String description, BigDecimal avgRating, int soldCount,
            int reviewCount, int price, int maxPrice, long stock, Instant updatedAt) {}

    /** Đếm đúng tập sản phẩm công khai có biến thể hợp lệ. */
    public long count(CatalogFilter filter) {
        var params = parameters(filter);
        return jdbc.queryForObject("SELECT count(*) " + SOURCE + where(filter, params), params, Long.class);
    }

    /** Lấy trang sản phẩm với thứ tự whitelist và không có N+1 query cho quan hệ. */
    public List<Product> products(CatalogFilter filter, int page) {
        var params = parameters(filter).addValue("limit", filter.size()).addValue("offset", (long) page * filter.size());
        String sort = switch (filter.sort()) {
            case "price-asc" -> "offer.price ASC, p.id ASC";
            case "price-desc" -> "offer.price DESC, p.id ASC";
            case "name" -> "lower(p.name) ASC, p.id ASC";
            case "newest" -> "p.created_at DESC, p.id DESC";
            default -> "coalesce(p.sold_count, 0) DESC, coalesce(p.avg_rating, 0) DESC, p.id DESC";
        };
        return load(select() + SOURCE + where(filter, params) + " ORDER BY " + sort + " LIMIT :limit OFFSET :offset", params);
    }

    /** Chi tiết chỉ trả sản phẩm đã xuất bản có ít nhất một biến thể hợp lệ. */
    public List<Product> product(int id) {
        return load(select() + SOURCE + " WHERE p.is_published = true AND p.id = :id", new MapSqlParameterSource("id", id));
    }

    /** Danh sách thương hiệu theo ID thật. */
    public List<Brand> brands() {
        return jdbc.query("SELECT id, name FROM brands ORDER BY lower(name), id", Map.of(),
                (rs, row) -> new Brand(rs.getInt("id"), rs.getString("name")));
    }

    /** Danh sách danh mục có parentId để dựng cây và breadcrumb. */
    public List<Category> categories() {
        return jdbc.query("SELECT id, parent, name FROM categories ORDER BY id", Map.of(),
                (rs, row) -> new Category(rs.getInt("id"), rs.getObject("parent", Integer.class), rs.getString("name")));
    }

    /** Tập giá trị thông số từ các biến thể hợp lệ của sản phẩm công khai. */
    public List<SpecificationFacet> specificationFacets() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        jdbc.query("""
                SELECT DISTINCT s.specification_name, s.specification_value
                FROM variant_specifications s
                JOIN product_variants v ON v.id = s.variant_id
                JOIN products p ON p.id = v.product_id
                WHERE p.is_published = true AND s.specification_name <> 'kind'
                  AND s.specification_name IS NOT NULL AND s.specification_value IS NOT NULL
                  AND v.price >= 0 AND v.quantity >= 0
                ORDER BY s.specification_name, s.specification_value
                """, new MapSqlParameterSource(), rs -> {
                    values.computeIfAbsent(rs.getString(1), key -> new ArrayList<>()).add(rs.getString(2));
                });
        return values.entrySet().stream().map(entry -> new SpecificationFacet(entry.getKey(), entry.getValue())).toList();
    }

    /** Mỗi review chỉ được đếm một lần dù có nhiều lịch sử sửa. */
    public long reviewCount(int productId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM reviews r
                JOIN order_items item ON item.id = r.order_item_id
                JOIN product_variants v ON v.id = item.variant_id
                WHERE v.product_id = :id
                AND EXISTS (SELECT 1 FROM review_histories h WHERE h.review_id = r.id)
                """, Map.of("id", productId), Long.class);
    }

    /** Lấy bản sửa mới nhất của từng review; không công khai email người viết. */
    public List<Review> reviews(int productId, int page, int size) {
        return jdbc.query("""
                SELECT r.id, coalesce(nullif(trim(u.full_name), ''), u.username) AS author,
                       h.rating, h.comment, h.created_at
                FROM reviews r
                JOIN order_items item ON item.id = r.order_item_id
                JOIN product_variants v ON v.id = item.variant_id
                JOIN LATERAL (SELECT * FROM review_histories rh WHERE rh.review_id = r.id
                              ORDER BY rh.created_at DESC, rh.id DESC LIMIT 1) h ON true
                JOIN users u ON u.id = h.user_id
                WHERE v.product_id = :id ORDER BY h.created_at DESC, r.id DESC LIMIT :limit OFFSET :offset
                """, new MapSqlParameterSource("id", productId).addValue("limit", size).addValue("offset", (long) page * size),
                (rs, row) -> new Review(rs.getInt("id"), rs.getString("author"), rs.getInt("rating"),
                        rs.getString("comment"), rs.getTimestamp("created_at").toInstant()));
    }

    /** Chọn các cột nền dùng chung cho danh sách và chi tiết. */
    private String select() {
        return """
                SELECT p.id, p.default_variant_id, p.name, p.description, coalesce(p.avg_rating, 0) AS avg_rating,
                  coalesce(p.sold_count, 0) AS sold_count, p.updated_at, offer.price, offer.max_price, offer.stock,
                  (SELECT count(*) FROM reviews r
                   JOIN order_items item ON item.id = r.order_item_id
                   JOIN product_variants v ON v.id = item.variant_id
                   WHERE v.product_id = p.id
                   AND EXISTS (SELECT 1 FROM review_histories h WHERE h.review_id = r.id)) AS review_count
                """;
    }

    /** Bind mọi giá trị query và escape ký tự LIKE để tìm kiếm chuỗi nguyên văn. */
    private MapSqlParameterSource parameters(CatalogFilter filter) {
        String escaped = filter.q().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return new MapSqlParameterSource("q", "%" + escaped + "%")
                .addValue("category", filter.category()).addValue("brands", filter.brands())
                .addValue("min", filter.minPrice()).addValue("max", filter.maxPrice());
    }

    /** Ghép điều kiện lọc, gồm danh mục con và từng nhóm thông số được chọn. */
    private String where(CatalogFilter filter, MapSqlParameterSource params) {
        var sql = new StringBuilder(" WHERE p.is_published = true");
        if (!filter.q().isBlank()) sql.append("""
                 AND (p.name ILIKE :q ESCAPE '\\' OR p.description ILIKE :q ESCAPE '\\'
                   OR EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.sku ILIKE :q ESCAPE '\\')
                   OR EXISTS (SELECT 1 FROM variant_specifications s JOIN product_variants v ON v.id = s.variant_id
                              WHERE v.product_id = p.id AND v.price >= 0 AND v.quantity >= 0
                                AND s.specification_value ILIKE :q ESCAPE '\\')
                   OR EXISTS (SELECT 1 FROM brands b WHERE b.id = p.brand_id AND b.name ILIKE :q ESCAPE '\\'))
                """);
        if (filter.category() != null) sql.append("""
                 AND EXISTS (WITH RECURSIVE tree(id) AS (
                   SELECT id FROM categories WHERE id = :category
                   UNION SELECT c.id FROM categories c JOIN tree t ON c.parent = t.id
                 ) SELECT 1 FROM product_categories pc JOIN tree t ON t.id = pc.cat_id WHERE pc.product_id = p.id)
                """);
        if (!filter.brands().isEmpty()) sql.append(" AND p.brand_id IN (:brands)");
        if (filter.minPrice() != null) sql.append(" AND offer.price >= :min");
        if (filter.maxPrice() != null) sql.append(" AND offer.price <= :max");
        if (filter.inStock()) sql.append(" AND offer.stock > 0");
        if (!filter.specifications().isEmpty()) {
            sql.append(" AND EXISTS (SELECT 1 FROM product_variants selected_variant WHERE selected_variant.product_id = p.id")
                    .append(" AND selected_variant.price >= 0 AND selected_variant.quantity >= 0");
            int index = 0;
            for (var entry : filter.specifications().entrySet()) {
                String key = "spec" + index++;
                sql.append(" AND EXISTS (SELECT 1 FROM variant_specifications s WHERE s.variant_id = selected_variant.id AND s.specification_name = :")
                        .append(key).append("Name AND s.specification_value IN (:").append(key).append("Values))");
                params.addValue(key + "Name", entry.getKey()).addValue(key + "Values", entry.getValue());
            }
            sql.append(")");
        }
        return sql.toString();
    }

    /** Tải quan hệ theo lô cho tối đa một trang; không sinh truy vấn theo từng sản phẩm. */
    private List<Product> load(String sql, MapSqlParameterSource params) {
        var rows = jdbc.query(sql, params, (rs, row) -> new ProductRow(rs.getInt("id"),
                rs.getObject("default_variant_id", Integer.class), rs.getString("name"),
                rs.getString("description"), rs.getBigDecimal("avg_rating"), rs.getInt("sold_count"), rs.getInt("review_count"),
                rs.getInt("price"), rs.getInt("max_price"), rs.getLong("stock"), rs.getTimestamp("updated_at").toInstant()));
        if (rows.isEmpty()) return List.of();
        var ids = new MapSqlParameterSource("ids", rows.stream().map(ProductRow::id).toList());
        Map<Integer, List<Brand>> brands = new HashMap<>();
        Map<Integer, List<Category>> categories = new HashMap<>();
        Map<Integer, List<Specification>> specifications = new HashMap<>();
        Map<Integer, List<Specification>> variantSpecifications = new HashMap<>();
        Map<Integer, List<Variant>> variants = new HashMap<>();
        Map<Integer, List<Image>> images = new HashMap<>();
        jdbc.query("SELECT p.id AS product_id, b.* FROM products p JOIN brands b ON b.id = p.brand_id WHERE p.id IN (:ids) ORDER BY b.id", ids,
                rs -> { brands.computeIfAbsent(rs.getInt("product_id"), key -> new ArrayList<>()).add(new Brand(rs.getInt("id"), rs.getString("name"))); });
        jdbc.query("SELECT pc.product_id, c.* FROM product_categories pc JOIN categories c ON c.id = pc.cat_id WHERE pc.product_id IN (:ids) ORDER BY c.id", ids,
                rs -> { categories.computeIfAbsent(rs.getInt("product_id"), key -> new ArrayList<>()).add(new Category(rs.getInt("id"), rs.getObject("parent", Integer.class), rs.getString("name"))); });
        jdbc.query("""
                SELECT v.id AS variant_id, v.product_id, s.specification_name, s.specification_value
                FROM variant_specifications s JOIN product_variants v ON v.id = s.variant_id
                WHERE v.product_id IN (:ids) AND v.price >= 0 AND v.quantity >= 0
                ORDER BY v.product_id, s.specification_name, s.specification_value, v.id
                """, ids,
                rs -> {
                    var specification = new Specification(rs.getString("specification_name"), rs.getString("specification_value"));
                    variantSpecifications.computeIfAbsent(rs.getInt("variant_id"), key -> new ArrayList<>()).add(specification);
                    var productSpecifications = specifications.computeIfAbsent(rs.getInt("product_id"), key -> new ArrayList<>());
                    if (!productSpecifications.contains(specification)) productSpecifications.add(specification);
                });
        jdbc.query("SELECT * FROM product_variants WHERE product_id IN (:ids) AND price >= 0 AND quantity >= 0 ORDER BY price, id", ids,
                rs -> { variants.computeIfAbsent(rs.getInt("product_id"), key -> new ArrayList<>()).add(new Variant(
                        rs.getInt("id"), rs.getString("sku"), rs.getInt("price"), rs.getInt("quantity"),
                        rs.getObject("image_id", Integer.class), variantSpecifications.getOrDefault(rs.getInt("id"), List.of()))); });
        jdbc.query("SELECT * FROM product_images WHERE product_id IN (:ids) ORDER BY is_primary DESC NULLS LAST, id", ids,
                rs -> { images.computeIfAbsent(rs.getInt("product_id"), key -> new ArrayList<>()).add(new Image(rs.getInt("id"), rs.getString("image_url"), rs.getBoolean("is_primary"))); });
        return rows.stream().map(row -> new Product(row.id(), row.defaultVariantId(), row.name(), row.description(), row.avgRating(), row.soldCount(),
                row.reviewCount(), row.price(), row.maxPrice(), row.stock(), row.updatedAt(), brands.getOrDefault(row.id(), List.of()),
                categories.getOrDefault(row.id(), List.of()), specifications.getOrDefault(row.id(), List.of()),
                variants.getOrDefault(row.id(), List.of()), images.getOrDefault(row.id(), List.of()))).toList();
    }
}
