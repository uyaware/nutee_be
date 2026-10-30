package com.nutee.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nutee.backend.cart.service.CartService;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.security.HmacTokenService;
import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
@Sql("/db/fixture/catalog.sql")
class CartIT {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired HmacTokenService tokens;
    @Autowired CartService cart;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test
    void addsMergesAndKeepsVariantsSeparateThenUpdatesRemovesAndClears() throws Exception {
        Cookie cookie = cookie(OWNER);
        mvc.perform(get("/api/v1/cart").cookie(cookie)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items").isEmpty());
        add(cookie, 11, 1, 200);
        add(cookie, 11, 1, 200);
        mvc.perform(post("/api/v1/cart/items").cookie(cookie).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantId\":12,\"quantity\":1,\"price\":1,\"userId\":\"forged\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ownerId").value(OWNER.toString()))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].variantId").value(11))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[1].sku").value("TEST-LAPTOP-16"))
                .andExpect(jsonPath("$.items[1].price").value(12000000))
                .andExpect(jsonPath("$.items[1].imageUrl").value("/test-laptop.svg"))
                .andExpect(jsonPath("$.items[1].specifications[0].value").value("16 GB"))
                .andExpect(jsonPath("$.quantity").value(3)).andExpect(jsonPath("$.subtotal").value(32000000));
        mvc.perform(put("/api/v1/cart/items/11").cookie(cookie).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subtotal").value(22000000));
        for (int repeat = 0; repeat < 2; repeat++) {
            mvc.perform(delete("/api/v1/cart/items/11").cookie(cookie).with(csrf()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        }
        for (int repeat = 0; repeat < 2; repeat++) {
            mvc.perform(delete("/api/v1/cart").cookie(cookie).with(csrf()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                    .andExpect(jsonPath("$.quantity").value(0)).andExpect(jsonPath("$.subtotal").value(0));
        }
        assertThat(jdbc.queryForObject("SELECT quantity FROM product_variants WHERE id = 11", Integer.class)).isEqualTo(2);
    }

    @Test
    void validatesIntegerQuantitiesAndRejectsUnavailableOrOverstockedVariantsWithoutChangingCart() throws Exception {
        Cookie cookie = cookie(OWNER);
        for (String payload : List.of("{}", "{\"variantId\":11,\"quantity\":0}",
                "{\"variantId\":11,\"quantity\":100}", "{\"variantId\":11,\"quantity\":1.5}",
                "{\"variantId\":11.5,\"quantity\":1}")) {
            mvc.perform(post("/api/v1/cart/items").cookie(cookie).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(payload)).andExpect(status().isBadRequest());
        }
        add(cookie, 999999, 1, 404);
        for (int id : new int[] {31, 41, 51}) {
            add(cookie, id, 1, 409);
        }
        add(cookie, 11, 2, 200);
        add(cookie, 11, 1, 409);
        mvc.perform(put("/api/v1/cart/items/11").cookie(cookie).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":3}")).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/cart/items/0").cookie(cookie).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":1}")).andExpect(status().isBadRequest());
        jdbc.update("UPDATE product_variants SET quantity = 200 WHERE id = 12");
        entityManager.clear();
        add(cookie, 12, 99, 200);
        add(cookie, 12, 1, 409);
        mvc.perform(get("/api/v1/cart").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(101));
    }

    @Test
    void reloadsCurrentPricesAndStockAndKeepsUnavailableRowsRemovable() throws Exception {
        Cookie cookie = cookie(OWNER);
        add(cookie, 11, 2, 200);
        jdbc.update("UPDATE product_variants SET quantity = 1, price = 11000000 WHERE id = 11");
        entityManager.clear();
        mvc.perform(get("/api/v1/cart").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("insufficient-stock"))
                .andExpect(jsonPath("$.items[0].price").value(11000000))
                .andExpect(jsonPath("$.hasIssues").value(true)).andExpect(jsonPath("$.subtotal").value(0));
        jdbc.update("UPDATE product_variants SET quantity = 0 WHERE id = 11");
        entityManager.clear();
        mvc.perform(get("/api/v1/cart").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("out-of-stock"));
        jdbc.update("UPDATE products SET is_published = false WHERE id = 1");
        jdbc.update("UPDATE product_variants SET price = NULL WHERE id = 11");
        entityManager.clear();
        mvc.perform(get("/api/v1/cart").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("hidden"))
                .andExpect(jsonPath("$.items[0].price").isEmpty())
                .andExpect(jsonPath("$.items[0].lineTotal").isEmpty());
        mvc.perform(delete("/api/v1/cart/items/11").cookie(cookie).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void requiresAuthenticationCsrfAndActiveOwnershipForEveryCartOperation() throws Exception {
        Cookie owner = cookie(OWNER);
        mvc.perform(get("/api/v1/cart")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/cart/items").cookie(owner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantId\":11,\"quantity\":1}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/cart").with(csrf())).andExpect(status().isUnauthorized());
        add(owner, 11, 1, 200);
        User other = users.saveAndFlush(User.create("other", "other@example.invalid", null, null, false, null));
        Cookie otherCookie = cookie(other.getId());
        mvc.perform(get("/api/v1/cart").cookie(otherCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(put("/api/v1/cart/items/11").cookie(otherCookie).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/cart/items/11").cookie(otherCookie).with(csrf())).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/cart").cookie(otherCookie).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/cart").cookie(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
        jdbc.update("UPDATE users SET is_active = false WHERE id = ?", OWNER);
        entityManager.clear();
        mvc.perform(get("/api/v1/cart").cookie(owner)).andExpect(status().isUnauthorized());
        add(owner, 12, 1, 401);
        mvc.perform(delete("/api/v1/cart").cookie(owner).with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void readsVariantImagesAndSpecificationsInBatches() throws Exception {
        Cookie cookie = cookie(OWNER);
        for (int id : new int[] {11, 12, 21}) {
            jdbc.update("INSERT INTO cart_items (user_id, variant_id, quantity) VALUES (?, ?, 1)", OWNER, id);
        }
        entityManager.clear();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mvc.perform(get("/api/v1/cart").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3));
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(5);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Sql(statements = "SELECT 1")
    void concurrentAddsCreateOneRowWithoutLosingQuantityOrExceedingStock() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user = users.saveAndFlush(User.create("cart_" + suffix, suffix + "@example.invalid", null, null, false, null));
        Integer productId = jdbc.queryForObject("INSERT INTO products (name, is_published) VALUES ('Concurrent cart', true) RETURNING id", Integer.class);
        Integer variantId = jdbc.queryForObject("INSERT INTO product_variants (product_id, sku, price, quantity) VALUES (?, ?, 100, 3) RETURNING id",
                Integer.class, productId, "CART-" + suffix);
        try {
            assertThat(parallelAdds(user.getId(), variantId)).containsExactlyInAnyOrder(200, 200);
            assertThat(parallelAdds(user.getId(), variantId)).containsExactlyInAnyOrder(200, 409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items WHERE user_id = ?", Integer.class, user.getId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE user_id = ?", Integer.class, user.getId())).isEqualTo(3);
        } finally {
            jdbc.update("DELETE FROM cart_items WHERE user_id = ?", user.getId());
            jdbc.update("DELETE FROM product_variants WHERE id = ?", variantId);
            jdbc.update("DELETE FROM products WHERE id = ?", productId);
            users.deleteById(user.getId());
        }
    }

    private List<Integer> parallelAdds(UUID owner, int variantId) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> add = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start latch timed out");
                try {
                    cart.add(owner, variantId, 1);
                    return 200;
                } catch (ApiException exception) {
                    return exception.getStatus().value();
                }
            };
            var first = executor.submit(add);
            var second = executor.submit(add);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
    }

    private Cookie cookie(UUID userId) {
        return new Cookie("accessToken", tokens.issue(users.findById(userId).orElseThrow()).accessToken());
    }

    private void add(Cookie cookie, int variantId, int quantity, int expected) throws Exception {
        mvc.perform(post("/api/v1/cart/items").cookie(cookie).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantId\":" + variantId + ",\"quantity\":" + quantity + "}"))
                .andExpect(status().is(expected));
    }
}
