package com.nutee.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutee.backend.cart.service.CartService;
import com.nutee.backend.security.HmacTokenService;
import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers
@Transactional @Sql("/db/fixture/catalog.sql")
class DiscountIT {
    static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired HmacTokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired CartService cart;
    Cookie admin;
    Cookie customer;

    @BeforeEach void account() {
        User user = users.saveAndFlush(User.create("admin_" + UUID.randomUUID(), UUID.randomUUID() + "@example.invalid", "test-hash", "Admin", false, null));
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", user.getId());
        entityManager.clear();
        admin = cookie(user.getId()); customer = cookie(OWNER);
    }

    @Test void managementRequiresActiveAdminAndCsrfAndValidatesRules() throws Exception {
        mvc.perform(get("/api/v1/management/promotions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/management/vouchers").cookie(customer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/management/promotions").cookie(admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(promotion("SALE10", 10))))
                .andExpect(status().isForbidden());
        for (String endpoint : List.of("promotions", "vouchers")) {
            mvc.perform(post("/api/v1/management/" + endpoint).cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }
        var invalid = promotion("BAD101", 101);
        mvc.perform(post("/api/v1/management/promotions").cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/management/promotions?status=unknown").cookie(admin)).andExpect(status().isBadRequest());
        var voucher = voucher("PRIVATE", "user", List.of());
        mvc.perform(post("/api/v1/management/vouchers").cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(voucher)))
                .andExpect(status().isBadRequest());
        String subject = tokens.decodeAccess(admin.getValue()).getSubject();
        jdbc.update("UPDATE users SET is_active = false WHERE id = ?", UUID.fromString(subject)); entityManager.clear();
        mvc.perform(get("/api/v1/management/promotions").cookie(admin)).andExpect(status().isForbidden());
    }

    @Test void promotionCrudChangesEffectivePricesAndDatabasePriceFilteringWithoutChangingBasePrice() throws Exception {
        UUID id = create("promotions", promotion("SALE10", 10));
        mvc.perform(get("/api/v1/products/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(9000000)).andExpect(jsonPath("$.variants[0].originalPrice").value(10000000))
                .andExpect(jsonPath("$.variants[0].promotionCode").value("SALE10"));
        mvc.perform(get("/api/v1/products?minPrice=9000000&maxPrice=9000000&sort=price-asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(1));
        assertThat(jdbc.queryForObject("SELECT price FROM product_variants WHERE id = 11", Integer.class)).isEqualTo(10000000);
        mvc.perform(get("/api/v1/management/promotions?q=sale&status=active&size=1").cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].productIds[0]").value(1));
        mvc.perform(patch("/api/v1/management/promotions/" + id + "/active").cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"isActive\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("paused"));
        mvc.perform(get("/api/v1/products/1")).andExpect(jsonPath("$.price").value(10000000));
        var update = promotion("SALE10", 100); update.put("type", "fixed"); update.put("value", 20000000);
        mvc.perform(put("/api/v1/management/promotions/" + id).cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/1")).andExpect(jsonPath("$.price").value(0));
        mvc.perform(delete("/api/v1/management/promotions/" + id).cookie(admin).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/products/1")).andExpect(jsonPath("$.price").value(10000000));
    }

    @Test void percentagePricingRoundsDownTheDiscountAndAvoidsIntegerOverflowInDatabaseFilters() throws Exception {
        create("promotions", promotion("ROUND10", 10));
        jdbc.update("UPDATE product_variants SET price = 101 WHERE id IN (11, 12)"); entityManager.clear();
        mvc.perform(get("/api/v1/products/1")).andExpect(jsonPath("$.price").value(91));
        mvc.perform(get("/api/v1/products?minPrice=91&maxPrice=91")).andExpect(jsonPath("$.content[0].id").value(1));
        jdbc.update("UPDATE product_variants SET price = 1999999999 WHERE id IN (11, 12)"); entityManager.clear();
        mvc.perform(get("/api/v1/products/1")).andExpect(jsonPath("$.price").value(1800000000));
        mvc.perform(get("/api/v1/products?minPrice=1800000000&maxPrice=1800000000")).andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test void duplicateCodesAreRejectedCaseInsensitivelyByTheDatabase() throws Exception {
        create("vouchers", voucher("DUPLICATE", "all", List.of()));
        mvc.perform(post("/api/v1/management/vouchers").cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(voucher(" duplicate ", "all", List.of())))).andExpect(status().isConflict());
    }

    @Test void quoteUsesOnlySelectedOwnedCartRowsStacksDiscountsAndDoesNotConsumeVoucher() throws Exception {
        create("promotions", promotion("SALE10", 10));
        UUID voucherId = create("vouchers", voucher("CHECK10", "all", List.of()));
        cart.add(OWNER, 11, 1); cart.add(OWNER, 12, 1);
        for (int repeat = 0; repeat < 2; repeat++) {
            mvc.perform(post("/api/v1/checkout/quote").cookie(customer).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"variantIds\":[11],\"voucherCode\":\" check10 \",\"subtotal\":1,\"userId\":\"forged\"}"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.subtotal").value(10000000))
                    .andExpect(jsonPath("$.promotionDiscount").value(1000000)).andExpect(jsonPath("$.voucherDiscount").value(500000))
                    .andExpect(jsonPath("$.total").value(8500000)).andExpect(jsonPath("$.voucherCode").value("CHECK10"))
                    .andExpect(jsonPath("$.shippingConfirmed").value(false));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE voucher_id = ?", Integer.class, voucherId)).isZero();
        assertThat(jdbc.queryForObject("SELECT quantity FROM vouchers WHERE id = ?", Integer.class, voucherId)).isEqualTo(2);
        mvc.perform(post("/api/v1/checkout/quote").cookie(customer).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantIds\":[21]}" )).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/checkout/quote").cookie(customer).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantIds\":[11,11]}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/checkout/quote").cookie(customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"variantIds\":[11]}" )).andExpect(status().isForbidden());
    }

    @Test void privateVoucherIsNotExposedOrAppliedForAnotherAccountAndRulesEnforceTimeAndMinimum() throws Exception {
        User other = users.saveAndFlush(User.create("other", "other@example.invalid", "test-hash", "Other", false, null));
        UUID id = create("vouchers", voucher("PRIVATE10", "user", List.of(other.getId())));
        cart.add(OWNER, 11, 1);
        mvc.perform(get("/api/v1/vouchers").cookie(customer)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        quote("PRIVATE10", 404);
        mvc.perform(get("/api/v1/vouchers").cookie(cookie(other.getId()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("PRIVATE10")).andExpect(jsonPath("$.content[0].users").doesNotExist());
        UUID publicId = create("vouchers", voucher("PUBLIC10", "all", List.of()));
        jdbc.update("UPDATE vouchers SET min_order = 11000000 WHERE id = ?", publicId); entityManager.clear(); quote("PUBLIC10", 409);
        jdbc.update("UPDATE vouchers SET min_order = 0, end_date = now() - interval '1 day' WHERE id = ?", publicId); entityManager.clear(); quote("PUBLIC10", 409);
        mvc.perform(delete("/api/v1/management/vouchers/" + id).cookie(admin).with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_vouchers WHERE voucher_id = ?", Integer.class, id)).isZero();
    }

    @Test void voucherCountsOrderUsageAndCancelledOrdersReleaseQuotaButKeepHistory() throws Exception {
        UUID id = create("vouchers", voucher("QUOTA10", "all", List.of()));
        cart.add(OWNER, 11, 1);
        jdbc.update("INSERT INTO orders (order_code, user_id, voucher_id, status) VALUES ('DISC000001', ?, ?, 'pending')", OWNER, id);
        quote("QUOTA10", 409);
        jdbc.update("UPDATE orders SET status = 'cancelled' WHERE order_code = 'DISC000001'"); entityManager.clear(); quote("QUOTA10", 200);
        mvc.perform(delete("/api/v1/management/vouchers/" + id).cookie(admin).with(csrf())).andExpect(status().isConflict());
        jdbc.update("INSERT INTO orders (order_code, user_id, voucher_id, status) VALUES ('DISC000002', ?, ?, 'paid'), ('DISC000003', ?, ?, 'paid')", OWNER, id, OWNER, id);
        quote("QUOTA10", 409);
    }

    @Test void discountSeedIsRepeatableAndPreservesEditedRules() throws Exception {
        jdbc.update("UPDATE product_variants SET sku = 'NT260001V01' WHERE id = 11");
        String seed = Files.readString(Path.of("scripts/seed-discounts.sql")).replaceFirst("(?m)^BEGIN;", "").replaceFirst("(?m)^COMMIT;", "");
        jdbc.execute(seed);
        jdbc.update("UPDATE vouchers SET value = 77777 WHERE code = 'NT26_100K'");
        jdbc.execute(seed);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM promotions WHERE code LIKE 'NT26_%'", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vouchers WHERE code LIKE 'NT26_%'", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT value FROM vouchers WHERE code = 'NT26_100K'", Integer.class)).isEqualTo(77777);
        assertThat(jdbc.queryForObject("SELECT promotion_id::text FROM products WHERE id = 1", String.class)).isEqualTo("26000000-0000-0000-0000-000000000001");
    }

    private UUID create(String kind, Map<String, Object> body) throws Exception {
        var result = mvc.perform(post("/api/v1/management/" + kind).cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }
    private Map<String, Object> promotion(String code, int percent) {
        return new HashMap<>(Map.of("code", code, "type", "percent", "value", percent,
                "startsAt", Instant.now().minusSeconds(3600).toString(), "endsAt", Instant.now().plusSeconds(86400).toString(), "isActive", true, "productIds", List.of(1)));
    }
    private Map<String, Object> voucher(String code, String scope, List<UUID> userIds) {
        var body = promotion(code, 10); body.remove("productIds"); body.put("scope", scope); body.put("userIds", userIds);
        body.put("minOrder", 1000000); body.put("maxDiscount", 500000); body.put("quantity", 2); body.put("maxUsesPerUser", 1); return body;
    }
    private Cookie cookie(UUID id) { return new Cookie("accessToken", tokens.issue(users.findById(id).orElseThrow()).accessToken()); }
    private void quote(String code, int expected) throws Exception {
        mvc.perform(post("/api/v1/checkout/quote").cookie(customer).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("variantIds", List.of(11), "voucherCode", code)))).andExpect(status().is(expected));
    }
}
