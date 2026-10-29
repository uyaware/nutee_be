package com.nutee.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ có HTTP status và thông báo an toàn để trả về client. */
@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    /** Tạo lỗi nghiệp vụ với trạng thái HTTP và nội dung cụ thể. */
    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
