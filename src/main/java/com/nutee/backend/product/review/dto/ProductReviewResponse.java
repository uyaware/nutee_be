package com.nutee.backend.product.review.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** Bản sửa mới nhất của đánh giá; không công khai thông tin xác thực. */
@Schema(name="Review")
public record ProductReviewResponse(int id, String authorName, int rating, String comment, Instant createdAt) {}
