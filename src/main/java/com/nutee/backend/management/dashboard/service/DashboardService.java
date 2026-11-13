package com.nutee.backend.management.dashboard.service;

import com.nutee.backend.aftersale.repository.AfterSaleRepository;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.management.dashboard.dto.*;
import com.nutee.backend.order.repository.OrderRepository;
import com.nutee.backend.product.repository.ProductRepository;
import com.nutee.backend.promotion.repository.PromotionRepository;
import com.nutee.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dashboard tổng hợp trong DB, chỉ tải tối đa năm đơn và năm sản phẩm tồn thấp. */
@Service @Transactional(readOnly = true)
public class DashboardService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("dd/MM");
    private final OrderRepository orders;
    private final ProductRepository products;
    private final PromotionRepository promotions;
    private final UserRepository users;
    private final AfterSaleRepository afterSales;
    private final Clock clock;

    @Autowired
    public DashboardService(OrderRepository orders, ProductRepository products, PromotionRepository promotions,
            UserRepository users, AfterSaleRepository afterSales) {
        this(orders, products, promotions, users, afterSales, Clock.systemUTC());
    }

    DashboardService(OrderRepository orders, ProductRepository products, PromotionRepository promotions,
            UserRepository users, AfterSaleRepository afterSales, Clock clock) {
        this.orders = orders; this.products = products; this.promotions = promotions;
        this.users = users; this.afterSales = afterSales; this.clock = clock;
    }

    public OperationsDashboardResponse operations() {
        return new OperationsDashboardResponse(clock.instant(), orders.count(), orders.countByStatus("pending"), afterSales.countAwaitingReview());
    }

    /** Bao gồm ngày hiện tại theo giờ Việt Nam; giá trị đã thanh toán phân theo ngày tạo đơn, không phải báo cáo kế toán. */
    public DashboardResponse summary(int days) {
        if (days != 7 && days != 30) throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ hỗ trợ khoảng 7 hoặc 30 ngày.");
        Instant now = clock.instant();
        LocalDate today = now.atZone(ZONE).toLocalDate();
        LocalDate fromDate = today.minusDays(days - 1L);
        Instant from = fromDate.atStartOfDay(ZONE).toInstant();
        Instant until = today.plusDays(1).atStartOfDay(ZONE).toInstant();
        Map<String, Long> statuses = new LinkedHashMap<>();
        orders.dashboardStatuses(from, until).forEach(row -> statuses.put(row.getStatus() == null ? "unknown" : row.getStatus(), row.getCount()));
        int bucketDays = days == 7 ? 1 : 5;
        List<DashboardResponse.Bucket> buckets = new ArrayList<>();
        for (int offset = 0; offset < days; offset += bucketDays) {
            LocalDate start = fromDate.plusDays(offset);
            LocalDate end = start.plusDays(bucketDays);
            BigDecimal value = orders.dashboardPaidValue(start.atStartOfDay(ZONE).toInstant(), end.atStartOfDay(ZONE).toInstant());
            String label = LABEL.format(start) + (bucketDays == 1 ? "" : "–" + LABEL.format(end.minusDays(1)));
            buckets.add(new DashboardResponse.Bucket(label, value));
        }
        var recent = orders.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(from, until, PageRequest.of(0, 5))
                .stream().map(order -> new DashboardResponse.RecentOrder(order.getId(), order.getOrderCode(),
                        order.getUser() == null ? null : order.getUser().getFullName(), order.getStatus(), order.getTotal(), order.getCreatedAt())).toList();
        var stock = products.dashboardLowStock(PageRequest.of(0, 5)).stream()
                .map(row -> new DashboardResponse.LowStock(row.getId(), row.getName(), row.getStock())).toList();
        return new DashboardResponse(now, ZONE.getId(), fromDate, today, statuses.values().stream().mapToLong(Long::longValue).sum(),
                statuses.getOrDefault("pending", 0L), afterSales.countAwaitingReview(),
                buckets.stream().map(DashboardResponse.Bucket::value).reduce(BigDecimal.ZERO, BigDecimal::add),
                statuses, buckets, recent, stock, products.countByPublishedTrue(),
                promotions.countByActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(now, now), users.countByActiveTrue());
    }
}
