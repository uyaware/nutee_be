package com.nutee.backend.common.response;

public record ValidationError(String field, String message) {
}
