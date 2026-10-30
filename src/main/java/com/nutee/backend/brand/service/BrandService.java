package com.nutee.backend.brand.service;

import com.nutee.backend.brand.dto.BrandResponse;
import com.nutee.backend.brand.entity.Brand;
import com.nutee.backend.brand.repository.BrandRepository;
import com.nutee.backend.common.exception.ApiException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đọc thương hiệu và xử lý trường hợp ID không tồn tại. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandService {
    private final BrandRepository repository;

    /** Trả danh sách thương hiệu công khai. */
    public List<BrandResponse> list() {
        return repository.findAll(Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id")))
                .stream().map(this::response).toList();
    }

    /** Trả thương hiệu theo ID hoặc lỗi 404. */
    public BrandResponse get(int id) {
        return repository.findById(id).map(this::response)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Brand not found."));
    }

    /** Chuyển entity sang DTO trong transaction, giữ entity nội bộ backend. */
    private BrandResponse response(Brand brand) {
        return new BrandResponse(brand.getId(), brand.getName());
    }
}
