package com.nutee.backend.category.dto;

public record ManagementTaxonomyItem(int id, String name, Integer parentId, long productCount, long childCount) {}
