package com.nutee.backend.common.discount;

import com.nutee.backend.common.exception.ApiException;
import java.time.Instant;
import org.springframework.http.HttpStatus;

/** Quy tắc tiền VND dùng chung; số giảm luôn được chặn trong khoảng từ 0 đến tiền hàng. */
public final class Discounts {
    private Discounts() {}

    /** Phần trăm làm tròn xuống đến đồng, không dùng phép nhân int gây tràn số. */
    public static long amount(long base, String type, int value) {
        if (base <= 0 || value <= 0) return 0;
        if ("percent".equals(type)) return base / 100 * value + base % 100 * value / 100;
        return "fixed".equals(type) ? Math.min(base, value) : 0;
    }

    public static String status(boolean active, Instant start, Instant end, Instant now) {
        if (!active) return "paused";
        if (end == null || !end.isAfter(now)) return "expired";
        if (start == null || start.isAfter(now)) return "scheduled";
        return "active";
    }

    /** Từ chối điều kiện chéo không thể biểu diễn bằng validation của từng trường. */
    public static void validate(String type, int value, Instant start, Instant end) {
        if (!("fixed".equals(type) || "percent".equals(type)) || value < 1
                || ("percent".equals(type) && value > 100) || start == null || end == null || !end.isAfter(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mức giảm hoặc thời gian áp dụng không hợp lệ.");
        }
    }
}
