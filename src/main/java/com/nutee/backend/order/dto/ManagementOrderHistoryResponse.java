package com.nutee.backend.order.dto;

import java.time.Instant;

public record ManagementOrderHistoryResponse(String status, Instant occurredAt, String actorName) {}
