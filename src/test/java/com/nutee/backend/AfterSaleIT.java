package com.nutee.backend;

import static org.assertj.core.api.Assertions.*;

import com.nutee.backend.aftersale.dto.CreateAfterSaleRequest;
import com.nutee.backend.aftersale.service.AfterSaleService;
import com.nutee.backend.common.exception.ApiException;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Sql(scripts = "/db/fixture/catalog.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AfterSaleIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    @Autowired private AfterSaleService service;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach void prepare() {
        jdbc.update("DELETE FROM aftersale_request_histories");
        jdbc.update("DELETE FROM aftersale_requests");
        jdbc.update("UPDATE orders SET status = 'delivered' WHERE id = 101");
        jdbc.update("UPDATE order_items SET quantity = 3 WHERE id = 201");
    }

    @Test void persistsFullLineAndFiltersByLatestHistoryWithoutAddingStatusColumn() {
        var request = service.create(OWNER, new CreateAfterSaleRequest(201, "warranty", "Test problem", null));
        assertThat(request.quantity()).isEqualTo(3);
        assertThat(service.get(OWNER, request.id()).history()).hasSize(1);
        assertThat(service.list(OWNER, 0, 5, "pending", "all", "Test laptop").totalElements()).isEqualTo(1);
        service.transition(OWNER, request.id(), "pending", "reviewing");
        assertThat(service.list(OWNER, 0, 5, "pending", "all", "").content()).isEmpty();
        assertThat(service.list(null, 0, 5, "reviewing", "warranty", String.valueOf(request.id())).totalElements()).isEqualTo(1);
        var latest = service.get(OWNER, request.id());
        assertThat(latest.history()).hasSize(2);
        assertThat(latest.status()).isEqualTo("reviewing");
        assertThatThrownBy(() -> service.transition(OWNER, request.id(), "pending", "reviewing"))
                .isInstanceOf(ApiException.class);
        assertThat(service.get(OWNER, request.id()).history()).hasSize(2);
    }

    @Test void serializesConcurrentRequestsForSamePurchasedLine() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> create = () -> {
                start.await();
                try {
                    service.create(OWNER, new CreateAfterSaleRequest(201, "return", "Test problem", null));
                    return true;
                } catch (ApiException exception) {
                    if (exception.getStatus().value() != 409) throw exception;
                    return false;
                }
            };
            var first = executor.submit(create);
            var second = executor.submit(create);
            start.countDown();
            assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aftersale_requests", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aftersale_request_histories", Integer.class)).isEqualTo(1);
    }
}
