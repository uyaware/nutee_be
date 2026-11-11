package com.nutee.backend.order.dto;

import com.nutee.backend.order.entity.OrderHistory;
import java.time.Instant;

public record OrderHistoryResponse(String status, Instant occurredAt) {
    public static OrderHistoryResponse from(OrderHistory history) {
        return new OrderHistoryResponse(history.getStatus(), history.getOccurredAt());
    }
}
