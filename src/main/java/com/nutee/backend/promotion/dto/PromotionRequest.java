package com.nutee.backend.promotion.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Promotion tự động áp dụng trên giá từng biến thể thuộc những sản phẩm được chọn. */
public record PromotionRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9_-]{3,20}") String code,
        @NotBlank @Pattern(regexp = "fixed|percent") String type,
        @NotNull @Min(1) @Max(1000000000) Integer value,
        @NotNull Instant startsAt, @NotNull Instant endsAt, @NotNull Boolean isActive,
        @NotNull @Size(max = 200) List<@NotNull @Min(1) Integer> productIds,
        @NotNull @Pattern(regexp = "all|product") String scope) {
    public PromotionRequest {
        code = code == null ? null : code.strip().toUpperCase(Locale.ROOT);
        if (scope == null) scope = "product";
    }

    @AssertTrue(message = "Chọn ít nhất một sản phẩm hoặc chọn tất cả sản phẩm.")
    public boolean isProductSelectionValid() {
        return "all".equals(scope) || (productIds != null && !productIds.isEmpty());
    }
}
