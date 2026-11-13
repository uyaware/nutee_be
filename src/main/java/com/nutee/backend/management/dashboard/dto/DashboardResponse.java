package com.nutee.backend.management.dashboard.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Số liệu tổng hợp với khoảng thời gian và múi giờ rõ ràng. */
public record DashboardResponse(Instant asOf, String timezone, LocalDate fromDate, LocalDate throughDate,
        long orderCount, long pendingCount, long afterSaleCount, BigDecimal paidValue,
        Map<String, Long> statuses, List<Bucket> buckets, List<RecentOrder> recentOrders,
        List<LowStock> lowStock, long publishedProducts, long activePromotions, long activeUsers) {
    public record Bucket(String label, BigDecimal value) {}
    public record RecentOrder(int id, String code, String recipient, String status, BigDecimal total, Instant createdAt) {}
    public record LowStock(int id, String name, long stock) {}
}
