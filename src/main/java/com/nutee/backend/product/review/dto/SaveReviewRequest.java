package com.nutee.backend.product.review.dto;

import jakarta.validation.constraints.*;

/** Số sao bắt buộc; nhận xét là tùy chọn và giới hạn theo giao diện hiện có. */
public record SaveReviewRequest(@NotNull @Min(1) @Max(5) Integer rating, @Size(max = 1000) String comment) {}
