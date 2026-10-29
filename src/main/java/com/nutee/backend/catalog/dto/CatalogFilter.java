package com.nutee.backend.catalog.dto;

import com.nutee.backend.common.exception.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;

/** Bộ lọc catalog; page bắt đầu từ 0 và tham số được kiểm tra trước khi query. */
public record CatalogFilter(String q, Integer category, List<Integer> brands, Integer minPrice,
        Integer maxPrice, boolean inStock, String sort, int page, int size,
        Map<String, List<String>> specifications) {
    /** Đọc query, giới hạn phân trang và tránh bộ lọc quá lớn. */
    public static CatalogFilter from(MultiValueMap<String, String> params) {
        String q = params.getFirst("q") == null ? "" : params.getFirst("q").trim();
        if (q.length() > 160) throw invalid("q must not exceed 160 characters.");
        String sort = params.getFirst("sort") == null ? "featured" : params.getFirst("sort");
        if (!Set.of("featured", "price-asc", "price-desc", "name", "newest").contains(sort)) {
            throw invalid("Unsupported product sort.");
        }
        var brands = params.getOrDefault("brand", List.of()).stream()
                .map(value -> number(value, "brand", null, 1, Integer.MAX_VALUE)).distinct().toList();
        if (brands.size() > 20) throw invalid("At most 20 brands are allowed.");
        Integer min = number(params.getFirst("minPrice"), "minPrice", null, 0, Integer.MAX_VALUE);
        Integer max = number(params.getFirst("maxPrice"), "maxPrice", null, 0, Integer.MAX_VALUE);
        if (min != null && max != null && min > max) throw invalid("minPrice must not exceed maxPrice.");
        String inStock = params.getFirst("inStock");
        if (inStock != null && !Set.of("true", "false").contains(inStock)) throw invalid("inStock must be true or false.");
        Map<String, List<String>> specs = new LinkedHashMap<>();
        params.forEach((key, values) -> {
            if (key.startsWith("spec.")) {
                String name = key.substring(5);
                var selected = values.stream().map(String::trim).distinct().toList();
                if (name.isBlank() || name.length() > 80 || selected.isEmpty() || selected.size() > 20
                        || selected.stream().anyMatch(value -> value.isBlank() || value.length() > 160)) {
                    throw invalid("Invalid specification filter.");
                }
                specs.put(name, selected);
            }
        });
        if (specs.size() > 10) throw invalid("At most 10 specification filters are allowed.");
        return new CatalogFilter(q, number(params.getFirst("category"), "category", null, 1, Integer.MAX_VALUE),
                brands, min, max, "true".equals(inStock), sort,
                number(params.getFirst("page"), "page", 0, 0, 1_000_000),
                number(params.getFirst("size"), "size", 12, 1, 100), specs);
    }

    /** Chuyển số nguyên hoặc trả lỗi 400 khi định dạng/giới hạn không hợp lệ. */
    private static Integer number(String value, String field, Integer fallback, int min, int max) {
        if (value == null) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) { throw invalid("Invalid " + field + "."); }
    }

    /** Lỗi query không hợp lệ được trả thống nhất bằng ProblemDetail. */
    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
