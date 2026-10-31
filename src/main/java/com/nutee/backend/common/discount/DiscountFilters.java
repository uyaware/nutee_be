package com.nutee.backend.common.discount;

import com.nutee.backend.common.exception.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

/** Bộ lọc database dùng cho các rule có cùng thuộc tính mã, thời gian và trạng thái. */
public final class DiscountFilters {
    private DiscountFilters() {}

    public static <T> Specification<T> matching(String q, String status) {
        if (q.length() > 100 || !List.of("all", "active", "paused", "scheduled", "expired").contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bộ lọc ưu đãi không hợp lệ.");
        }
        Instant now = Instant.now();
        return (root, query, cb) -> {
            var filters = new ArrayList<Predicate>();
            String search = q.strip().toUpperCase(java.util.Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            filters.add(cb.like(cb.upper(root.get("code")), "%" + search + "%", '\\'));
            if ("paused".equals(status)) filters.add(cb.isFalse(root.get("active")));
            if ("active".equals(status)) filters.add(cb.and(cb.isTrue(root.get("active")),
                    cb.lessThanOrEqualTo(root.get("startsAt"), now), cb.greaterThan(root.get("endsAt"), now)));
            if ("scheduled".equals(status)) filters.add(cb.and(cb.isTrue(root.get("active")), cb.greaterThan(root.get("startsAt"), now)));
            if ("expired".equals(status)) filters.add(cb.and(cb.isTrue(root.get("active")), cb.lessThanOrEqualTo(root.get("endsAt"), now)));
            return cb.and(filters.toArray(Predicate[]::new));
        };
    }
}
