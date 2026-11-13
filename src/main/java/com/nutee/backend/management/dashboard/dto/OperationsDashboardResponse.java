package com.nutee.backend.management.dashboard.dto;

import java.time.Instant;

/** Số đếm vận hành toàn hệ thống dành cho nhân viên đang hoạt động. */
public record OperationsDashboardResponse(Instant asOf, long orderCount, long pendingCount, long afterSaleCount) {}
