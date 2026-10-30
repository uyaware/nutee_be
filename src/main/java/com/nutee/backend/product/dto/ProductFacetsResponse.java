package com.nutee.backend.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.nutee.backend.brand.dto.BrandResponse;
import com.nutee.backend.category.dto.CategoryResponse;
import com.nutee.backend.product.variant.specification.dto.SpecificationFacetResponse;
import java.util.List;

/** Thương hiệu, danh mục và thông số phục vụ bộ lọc sản phẩm. */
@Schema(name="Facets")
public record ProductFacetsResponse(List<BrandResponse> brands, List<CategoryResponse> categories,
        List<SpecificationFacetResponse> specifications) {}
