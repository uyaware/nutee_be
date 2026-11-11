package com.nutee.backend.category.service;

import com.nutee.backend.brand.entity.Brand;
import com.nutee.backend.brand.repository.BrandRepository;
import com.nutee.backend.category.dto.ManagementTaxonomyItem;
import com.nutee.backend.category.dto.ManagementTaxonomyRequest;
import com.nutee.backend.category.entity.Category;
import com.nutee.backend.category.repository.CategoryRepository;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.product.repository.ProductRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagementTaxonomyService {
    private final BrandRepository brands;
    private final CategoryRepository categories;
    private final ProductRepository products;

    @Transactional(readOnly = true)
    public List<ManagementTaxonomyItem> list(String kind, String query) {
        if (isCategory(kind)) return categories.findManagementItems(query);
        return brands.findManagementItems(query).stream()
                .map(row -> new ManagementTaxonomyItem((Integer) row[0], (String) row[1], null, (Long) row[2], 0))
                .toList();
    }

    @Transactional
    public void create(String kind, ManagementTaxonomyRequest request) {
        if (isCategory(kind)) {
            List<Category> locked = categories.findAllLocked();
            categories.save(Category.create(request.name().trim(), parent(locked, null, request.parentId())));
        } else {
            brands.save(Brand.create(request.name().trim()));
        }
    }

    @Transactional
    public void update(String kind, int id, ManagementTaxonomyRequest request) {
        if (isCategory(kind)) {
            List<Category> locked = categories.findAllLocked();
            Category category = locked.stream().filter(value -> value.getId().equals(id)).findFirst()
                    .orElseThrow(ManagementTaxonomyService::notFound);
            category.update(request.name().trim(), parent(locked, id, request.parentId()));
        } else {
            brands.findById(id).orElseThrow(ManagementTaxonomyService::notFound).rename(request.name().trim());
        }
    }

    @Transactional
    public void delete(String kind, int id) {
        if (isCategory(kind)) {
            Category category = categories.findById(id).orElseThrow(ManagementTaxonomyService::notFound);
            if (categories.countByParent_Id(id) > 0 || products.countByCategories_Id(id) > 0) throw inUse();
            categories.delete(category);
        } else {
            Brand brand = brands.findById(id).orElseThrow(ManagementTaxonomyService::notFound);
            if (products.countByBrand_Id(id) > 0) throw inUse();
            brands.delete(brand);
        }
    }

    private Category parent(List<Category> rows, Integer id, Integer parentId) {
        if (parentId == null) return null;
        Category selected = rows.stream().filter(value -> value.getId().equals(parentId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Danh mục cha không tồn tại."));
        Set<Integer> visited = new HashSet<>();
        for (Category current = selected; current != null; current = current.getParent()) {
            if (current.getId().equals(id) || !visited.add(current.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Danh mục cha không hợp lệ.");
            }
        }
        return selected;
    }

    private boolean isCategory(String kind) {
        if (!kind.equals("brands") && !kind.equals("categories")) throw notFound();
        return kind.equals("categories");
    }

    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bản ghi."); }
    private static ApiException inUse() { return new ApiException(HttpStatus.CONFLICT, "Bản ghi đang được sử dụng."); }
}
