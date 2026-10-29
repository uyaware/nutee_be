package com.nutee.backend.common.response;

/** Một lỗi validation gồm tên trường và thông báo cho người dùng. */
public record ValidationError(String field, String message) {
}
