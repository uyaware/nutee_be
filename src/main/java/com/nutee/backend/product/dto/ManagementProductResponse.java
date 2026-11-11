package com.nutee.backend.product.dto;

import java.time.Instant;
import java.util.List;

public record ManagementProductResponse(int id, String name, String description, boolean published,
        Integer brandId, List<Integer> categoryIds, Integer defaultVariantId, Instant updatedAt,
        List<Image> images, List<Variant> variants) {
    public record Image(int id, String url, boolean primary) {}
    public record Spec(String name, String value) {}
    public record Variant(int id, String sku, int price, int quantity, Integer imageId, List<Spec> specifications) {}
}
