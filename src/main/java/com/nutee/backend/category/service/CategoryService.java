package com.nutee.backend.category.service;

import com.nutee.backend.category.dto.CategoryResponse;
import com.nutee.backend.category.entity.Category;
import com.nutee.backend.category.repository.CategoryRepository;
import com.nutee.backend.common.exception.ApiException;
import java.util.List;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đọc danh mục và xử lý trường hợp ID không tồn tại. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository repository;

    /** Trả danh sách danh mục công khai. */
    public List<CategoryResponse> list() {
        return repository.findAll(Sort.by("id")).stream().map(this::response).toList();
    }

    /** Trả danh mục theo ID hoặc lỗi 404. */
    public CategoryResponse get(int id) {
        return repository.findById(id).map(this::response)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found."));
    }

    /** Chỉ đọc ID của parent LAZY; không tải cả cây danh mục khi tạo DTO. */
    private CategoryResponse response(Category category) {
        return new CategoryResponse(category.getId(), category.getName(),
                category.getParent() == null ? null : category.getParent().getId());
    }

    /** Lấy các ID hậu duệ bằng một query JPA; tập đã thăm ngăn vòng lặp khi cây có chu trình. */
    public Set<Integer> descendantIds(int id) {
        var children = new HashMap<Integer, List<Integer>>();
        for (var category : list()) {
            if (category.parentId() != null) {
                children.computeIfAbsent(category.parentId(), key -> new ArrayList<>()).add(category.id());
            }
        }
        var result = new HashSet<Integer>();
        var pending = new ArrayDeque<Integer>();
        pending.add(id);
        while (!pending.isEmpty()) {
            int current = pending.removeFirst();
            if (result.add(current)) pending.addAll(children.getOrDefault(current, List.of()));
        }
        return result;
    }
}
