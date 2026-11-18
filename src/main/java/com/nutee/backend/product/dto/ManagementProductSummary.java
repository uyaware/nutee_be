package com.nutee.backend.product.dto;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;

public record ManagementProductSummary(int id, String name, boolean published, Instant updatedAt,
        String imageUrl, String brand, List<String> categories, BigDecimal avgRating, Integer soldCount) {}
