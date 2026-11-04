package com.nutee.backend.product.variant.specification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Các giá trị thông số có sẵn để lọc sản phẩm. */
@Schema(name="SpecificationFacet")
public record SpecificationFacetResponse(String name, List<String> values) {}
