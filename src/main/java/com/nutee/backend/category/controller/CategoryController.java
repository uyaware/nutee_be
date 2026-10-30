package com.nutee.backend.category.controller;

import com.nutee.backend.category.dto.CategoryResponse;
import com.nutee.backend.category.service.CategoryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API đọc danh mục, giữ nguyên đường dẫn công khai. */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@SecurityRequirements
public class CategoryController {
    private final CategoryService service;

    @GetMapping
    public List<CategoryResponse> categories() {
        return service.list();
    }

    @GetMapping("/{id}")
    public CategoryResponse category(@PathVariable int id) {
        return service.get(id);
    }
}
