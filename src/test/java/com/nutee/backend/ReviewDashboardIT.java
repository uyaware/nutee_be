package com.nutee.backend;

import static org.assertj.core.api.Assertions.*;

import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.management.dashboard.service.DashboardService;
import com.nutee.backend.product.repository.ProductRepository;
import com.nutee.backend.product.review.dto.SaveReviewRequest;
import com.nutee.backend.product.review.repository.ProductReviewRepository;
import com.nutee.backend.product.review.service.ProductReviewService;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProductReviewService.class, DashboardService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ActiveProfiles("test")
@Testcontainers
@Sql(scripts = "/db/fixture/catalog.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ReviewDashboardIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    @Autowired private ProductReviewService reviews;
    @Autowired private DashboardService dashboard;
    @Autowired private ProductRepository products;
    @Autowired private ProductReviewRepository reviewRepository;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach void prepare() {
        jdbc.update("DELETE FROM aftersale_request_histories");
        jdbc.update("DELETE FROM aftersale_requests");
        jdbc.update("DELETE FROM review_histories WHERE id NOT IN (401, 402)");
        jdbc.update("DELETE FROM reviews WHERE id <> 301");
        jdbc.update("DELETE FROM order_items WHERE id <> 201");
        jdbc.update("DELETE FROM orders WHERE id <> 101");
        jdbc.update("DELETE FROM promotions");
        jdbc.update("UPDATE orders SET status = 'delivered', payment_status = 'paid', total_amount = 100000, created_at = now() - interval '1 day' WHERE id = 101");
        jdbc.update("UPDATE products SET avg_rating = 0");
    }

    @Test void editsOnlyOwnDeliveredPurchaseKeepsHistoryAndUpdatesOneReviewAverage() {
        var original = reviews.get(OWNER, 101, 201);
        assertThat(original.rating()).isEqualTo(5);
        var saved = reviews.save(OWNER, 101, 201, new SaveReviewRequest(2, "  New review  "));
        assertThat(saved.comment()).isEqualTo("New review");
        assertThat(reviews.get(OWNER, 101, 201).rating()).isEqualTo(2);
        assertThat(products.findById(1).orElseThrow().getAvgRating()).isEqualByComparingTo("2.0");
        assertThat(reviewRepository.countByOrderItem_Variant_Product_IdAndHistoriesIsNotEmpty(1)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review_histories WHERE review_id = 301", Integer.class)).isEqualTo(3);
        reviews.save(OWNER, 101, 201, new SaveReviewRequest(2, "New review"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review_histories WHERE review_id = 301", Integer.class)).isEqualTo(3);
        reviews.save(OWNER, 101, 201, new SaveReviewRequest(4, "Updated again"));
        assertThat(products.findById(1).orElseThrow().getAvgRating()).isEqualByComparingTo("4.0");
        assertThat(reviewRepository.findLatestByProductId(1, PageRequest.of(0, 10))).singleElement()
                .satisfies(value -> { assertThat(value.rating()).isEqualTo(4); assertThat(value.comment()).isEqualTo("Updated again"); });
    }

    @Test void rejectsOtherOwnersWrongItemsAndUndeliveredOrdersWithoutWriting() {
        assertThatThrownBy(() -> reviews.get(OWNER, 999, 201)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> reviews.save(OWNER, 101, 999, new SaveReviewRequest(5, "Test"))).isInstanceOf(ApiException.class);
        jdbc.update("UPDATE orders SET status = 'shipping' WHERE id = 101");
        assertThatThrownBy(() -> reviews.save(OWNER, 101, 201, new SaveReviewRequest(5, "Test"))).isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review_histories", Integer.class)).isEqualTo(2);
        jdbc.update("INSERT INTO users (id, username, email) VALUES ('00000000-0000-0000-0000-000000000002', 'other-buyer', 'other@example.invalid') ON CONFLICT DO NOTHING");
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000002");
        jdbc.update("UPDATE orders SET status = 'delivered' WHERE id = 101");
        assertThatThrownBy(() -> reviews.get(other, 101, 201)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> reviews.save(other, 101, 201, new SaveReviewRequest(1, "Foreign"))).isInstanceOf(ApiException.class);
    }

    @Test void simultaneousSubmissionsCreateOnlyOneReviewAndOneIdenticalRevision() throws Exception {
        jdbc.update("INSERT INTO order_items (id, order_id, variant_id, quantity, calculated_price) VALUES (202, 101, 11, 1, 1000)");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> submit = () -> { start.await(); return reviews.save(OWNER, 101, 202, new SaveReviewRequest(3, "Same review")).reviewId(); };
            var first = executor.submit(submit); var second = executor.submit(submit);
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reviews WHERE order_item_id = 202", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review_histories h JOIN reviews r ON h.review_id = r.id WHERE r.order_item_id = 202", Integer.class)).isEqualTo(1);
        assertThat(products.findById(1).orElseThrow().getAvgRating()).isEqualByComparingTo("4.0");
    }

    @Test void dashboardAggregatesPaidOrdersExcludesCancelledAndLimitsLowStock() {
        jdbc.update("INSERT INTO aftersale_requests (id, order_item_id, type, reason) VALUES (501, 201, 'warranty', 'Test'), "
                + "(502, 201, 'warranty', 'Test'), (503, 201, 'warranty', 'Test'), (504, 201, 'warranty', 'Test')");
        jdbc.update("INSERT INTO aftersale_request_histories (request_id, user_id, request_status, occurred_at) VALUES "
                + "(502, ?, 'pending', now()), (503, ?, 'reviewing', now()), (504, ?, 'pending', now()), (504, ?, 'completed', now())", OWNER, OWNER, OWNER, OWNER);
        jdbc.update("INSERT INTO promotions (id, code, applied_for, type, value, is_active, start_date, end_date) "
                + "VALUES (?, 'TESTACTIVE', 'product', 'percent', 10, true, now() - interval '1 day', now() + interval '1 day')", UUID.randomUUID());
        jdbc.update("INSERT INTO orders (id, order_code, user_id, status, payment_status, total_amount, created_at) VALUES "
                + "(102, 'TEST000102', ?, 'cancelled', 'paid', 900000, now()), (103, 'TEST000103', ?, 'confirmed', 'unpaid', 500000, now()), "
                + "(104, 'TEST000104', ?, 'shipping', 'paid', 200000, now() - interval '2 days'), "
                + "(105, 'TEST000105', ?, 'delivered', 'paid', 700000, now() - interval '40 days'), (106, 'TEST000106', ?, 'pending', 'unpaid', 50000, now())",
                OWNER, OWNER, OWNER, OWNER, OWNER);
        var summary = dashboard.summary(7);
        assertThat(summary.orderCount()).isEqualTo(5);
        assertThat(summary.pendingCount()).isEqualTo(1);
        assertThat(summary.paidValue()).isEqualByComparingTo("300000");
        assertThat(summary.buckets()).hasSize(7);
        assertThat(summary.statuses().get("cancelled")).isEqualTo(1);
        assertThat(summary.publishedProducts()).isEqualTo(4);
        assertThat(summary.activePromotions()).isEqualTo(1);
        assertThat(summary.afterSaleCount()).isEqualTo(3);
        assertThat(summary.lowStock()).extracting(value -> value.id()).containsExactly(3, 4, 2, 1);
        assertThat(summary.recentOrders()).hasSize(5);
        assertThat(summary.timezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(dashboard.operations().orderCount()).isEqualTo(6);
        assertThat(dashboard.operations().pendingCount()).isEqualTo(1);
        assertThat(dashboard.operations().afterSaleCount()).isEqualTo(3);
        assertThat(dashboard.summary(30).buckets()).hasSize(6);
        assertThatThrownBy(() -> dashboard.summary(8)).isInstanceOf(ApiException.class);
    }

    @Test void concurrentReviewsFromDifferentOrdersKeepProductAverageConsistent() throws Exception {
        jdbc.update("INSERT INTO orders (id, order_code, user_id, status) VALUES (102, 'TEST000102', ?, 'delivered')", OWNER);
        jdbc.update("INSERT INTO order_items (id, order_id, variant_id, quantity, calculated_price) VALUES (202, 101, 11, 1, 1000), (203, 102, 11, 1, 1000)");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return reviews.save(OWNER, 101, 202, new SaveReviewRequest(1, "First order")); });
            var second = executor.submit(() -> { start.await(); return reviews.save(OWNER, 102, 203, new SaveReviewRequest(3, "Second order")); });
            start.countDown();
            first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
        }
        assertThat(products.findById(1).orElseThrow().getAvgRating()).isEqualByComparingTo("3.0");
        assertThat(reviewRepository.countByOrderItem_Variant_Product_IdAndHistoriesIsNotEmpty(1)).isEqualTo(3);
    }
}
