package com.nutee.backend.product.review.dto;

import java.time.Instant;

/** Đánh giá của chính chủ; reviewId và rating null khi dòng hàng chưa có đánh giá. */
public record OwnReviewResponse(Integer reviewId, Integer rating, String comment, Instant updatedAt) {}
