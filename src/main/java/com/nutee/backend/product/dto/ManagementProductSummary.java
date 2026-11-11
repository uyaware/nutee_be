package com.nutee.backend.product.dto;

import java.time.Instant;

public record ManagementProductSummary(int id, String name, boolean published, Instant updatedAt) {}
