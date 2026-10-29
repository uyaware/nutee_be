package com.nutee.backend.common.response;

import java.util.List;
import org.springframework.data.domain.Page;

/** Định dạng phân trang ổn định khi một API cần trả danh sách nhiều trang. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    /** Sao chép danh sách để dữ liệu trang trả về không bị sửa ngoài ý muốn. */
    public PageResponse {
        content = List.copyOf(content);
    }

    /** Chuyển Page của Spring thành dữ liệu phân trang công khai. */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }
}
