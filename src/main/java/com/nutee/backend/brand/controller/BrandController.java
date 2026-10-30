package com.nutee.backend.brand.controller;

import com.nutee.backend.brand.dto.BrandResponse;
import com.nutee.backend.brand.service.BrandService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API đọc thương hiệu, giữ nguyên đường dẫn công khai. */
@RestController
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
@SecurityRequirements
public class BrandController {
    private final BrandService service;

    /** Danh sách thương hiệu. */
    @GetMapping
    public List<BrandResponse> brands() { return service.list(); }

    /** Chi tiết thương hiệu theo ID. */
    @GetMapping("/{id}")
    public BrandResponse brand(@PathVariable int id) { return service.get(id); }
}
