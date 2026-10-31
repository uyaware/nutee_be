package com.nutee.backend;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutee.backend.cart.service.CartService;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.order.dto.*;
import com.nutee.backend.order.service.OrderService;
import com.nutee.backend.payment.adapter.*;
import com.nutee.backend.payment.port.PaymentGateway;
import com.nutee.backend.payment.service.*;
import com.nutee.backend.security.HmacTokenService;
import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(properties = {"app.payment.shipping-fee=25000", "app.payment.reconcile-delay=3600000"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers
class PaymentIT {
    static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final String CHECKSUM = "offline-test-checksum-key";
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired CartService cart;
    @Autowired OrderService orders;
    @Autowired PaymentService payments;
    @Autowired PaymentTransactions transactions;
    @Autowired UserRepository users;
    @Autowired HmacTokenService tokens;
    @MockitoBean PaymentGateway gateway;
    Cookie customer;

    @BeforeEach void setup() throws Exception {
        jdbc.execute("TRUNCATE users, brands, categories, promotions, vouchers, payment_events RESTART IDENTITY CASCADE");
        new ResourceDatabasePopulator(new ClassPathResource("db/fixture/catalog.sql")).execute(jdbc.getDataSource());
        deliveryAddress(OWNER);
        customer = cookie(OWNER);
        var verifier = new PayOSAdapter(new PaymentProperties(true, "test-client", "test-api", CHECKSUM, "http://localhost:5173", 25000, 15), json);
        when(gateway.enabled()).thenReturn(true);
        when(gateway.verify(anyString())).thenAnswer(call -> verifier.verify(call.getArgument(0)));
        when(gateway.create(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            PaymentGateway.Request request = call.getArgument(0);
            return new PaymentGateway.Link(request.orderCode(), request.amount(), "VND", "link" + request.orderCode(), "https://pay.payos.vn/web/link" + request.orderCode());
        });
    }

    @Test void checkoutIsIdempotentUsesServerPriceAndKeepsSnapshots() throws Exception {
        cart.add(OWNER, 11, 1);
        var body = request("cod", 10025000);
        var first = orders.create(OWNER, body);
        var second = orders.create(OWNER, body);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(first.shipping()).isEqualTo(25000);
        assertThat(first.total()).isEqualByComparingTo("10025000");
        assertThat(first.recipient()).isEqualTo("Test reviewer");
        assertThat(first.phone()).isEqualTo("0912345678");
        assertThat(first.addressId()).isEqualTo(addressId(OWNER));
        assertThat(jdbc.queryForObject("SELECT user_address_id FROM orders WHERE id = ?", Integer.class, first.id())).isEqualTo(first.addressId());
        assertThat(stock()).isEqualTo(1);
        assertThat(cart.get(OWNER).items()).isEmpty();
        jdbc.update("UPDATE products SET name = 'Changed' WHERE id = 1");
        jdbc.update("UPDATE product_variants SET price = 12 WHERE id = 11");
        jdbc.update("UPDATE variant_specifications SET specification_value = 'Changed' WHERE variant_id = 11");
        assertThat(orders.get(OWNER, first.id()).items().getFirst().name()).isEqualTo("Test laptop");
        assertThat(orders.get(OWNER, first.id()).items().getFirst().price()).isEqualByComparingTo("10000000");
        assertThat(orders.get(OWNER, first.id()).items().getFirst().configuration()).isEqualTo("8 GB / 256 GB");
        var changed = new CreateOrderRequest(body.idempotencyKey(), body.variantIds(), "", body.expectedTotal(), body.addressId(), "Ghi chú khác", "cod");
        assertThatThrownBy(() -> orders.create(OWNER, changed)).isInstanceOf(ApiException.class).hasMessageContaining("nội dung khác");
        mvc.perform(get("/api/v1/orders").cookie(customer)).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test void checkoutUsesAccountContactAndRejectsAnotherUsersAddressOrIncompleteProfile() throws Exception {
        cart.add(OWNER, 11, 1);
        User other = users.saveAndFlush(User.create("delivery-other", "delivery-other@example.invalid", "hash", "Other", false, null));
        deliveryAddress(other.getId());
        var foreign = new CreateOrderRequest(UUID.randomUUID(), List.of(11), "", 10025000L, addressId(other.getId()), "", "cod");
        assertThatThrownBy(() -> orders.create(OWNER, foreign)).isInstanceOf(ApiException.class).hasMessageContaining("địa chỉ");
        jdbc.update("UPDATE users SET phone = NULL WHERE id = ?", OWNER);
        assertThatThrownBy(() -> orders.create(OWNER, request("cod", 10025000))).isInstanceOf(ApiException.class).hasMessageContaining("hồ sơ");
        assertThat(stock()).isEqualTo(2);
        jdbc.update("UPDATE users SET phone = '0987654321' WHERE id = ?", OWNER);
        var body = json.valueToTree(request("cod", 10025000));
        ((com.fasterxml.jackson.databind.node.ObjectNode) body).put("recipient", "Tên giả").put("phone", "0900000000").put("addressLine", "Địa chỉ giả");
        mvc.perform(post("/api/v1/orders").cookie(customer).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.recipient").value("Test reviewer"))
                .andExpect(jsonPath("$.phone").value("0987654321"))
                .andExpect(jsonPath("$.addressLine").value("12 Đường A, Phường 1, TP Hồ Chí Minh"));
    }

    @Test void rejectsPriceTamperingUnavailableGatewayAndCrossAccountAccess() throws Exception {
        cart.add(OWNER, 11, 1);
        assertThatThrownBy(() -> orders.create(OWNER, request("cod", 1))).isInstanceOf(ApiException.class);
        assertThat(stock()).isEqualTo(2);
        when(gateway.enabled()).thenReturn(false);
        assertThatThrownBy(() -> orders.create(OWNER, request("qr", 10025000))).isInstanceOf(ApiException.class);
        assertThat(cart.get(OWNER).items()).hasSize(1);
        var order = orders.create(OWNER, request("cod", 10025000));
        var stranger = users.saveAndFlush(User.create("stranger", "stranger@example.invalid", "hash", "Stranger", false, null));
        mvc.perform(get("/api/v1/orders/" + order.id()).cookie(cookie(stranger.getId()))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/orders/" + order.id() + "/cancel").cookie(customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/checkout/config").cookie(customer)).andExpect(jsonPath("$.shippingFee").value(25000));
    }

    @Test void signedWebhookIsPublicIdempotentAndIgnoresBrowserStatus() throws Exception {
        int id = qr(); long code = code(id);
        String body = webhook(code, 10025000, "VND", "link" + code, "bank-ref-1", "00");
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/v1/payments/payos/webhook").cookie(new Cookie("accessToken", "expired"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        assertThat(orders.get(OWNER, id).paymentStatus()).isEqualTo("paid");
        assertThat(orders.get(OWNER, id).status()).isEqualTo("confirmed");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_events", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payment_amount FROM orders WHERE id = ?", Long.class, id)).isEqualTo(10025000);
        payments.webhook(webhook(code, 10025000, "VND", "link" + code, "failed-ref", "01"));
        assertThat(orders.get(OWNER, id).paymentStatus()).isEqualTo("paid");
        mvc.perform(get("/api/v1/orders/" + id + "?payment=unpaid").cookie(customer)).andExpect(jsonPath("$.paymentStatus").value("paid"));
    }

    @Test void tamperedSignatureAndMismatchedMoneyCurrencyOrLinkNeverConfirmPayment() throws Exception {
        int id = qr(); long code = code(id);
        String body = webhook(code, 10025000, "VND", "link" + code, "tamper", "00").replace("10025000", "10025001");
        mvc.perform(post("/api/v1/payments/payos/webhook").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        assertThat(orders.get(OWNER, id).paymentStatus()).isEqualTo("pending");
        payments.webhook(webhook(code, 100, "VND", "link" + code, "underpaid", "00"));
        payments.webhook(webhook(code, 10025000, "USD", "link" + code, "currency", "00"));
        payments.webhook(webhook(code, 10025000, "VND", "other-link", "link", "00"));
        assertThat(orders.get(OWNER, id).paymentStatus()).isEqualTo("review");
        assertThat(stock()).isEqualTo(1);
    }

    @Test void duplicateReferenceWithDifferentSignedDataIsConflictAndUnknownProbeIsAcknowledged() throws Exception {
        int id = qr(); long code = code(id);
        payments.webhook(webhook(code, 10025000, "VND", "link" + code, "same-ref", "00"));
        mvc.perform(post("/api/v1/payments/payos/webhook").contentType(MediaType.APPLICATION_JSON)
                .content(webhook(code, 1, "VND", "link" + code, "same-ref", "00"))).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/payments/payos/webhook").contentType(MediaType.APPLICATION_JSON)
                .content(webhook(123, 3000, "VND", "sample", "verification-probe", "00"))).andExpect(status().isOk());
        assertThat(orders.get(OWNER, id).paymentStatus()).isEqualTo("paid");
    }

    @Test void retriesStablePaymentCodeAfterTimeoutAndNeverCallsProviderInTransaction() {
        cart.add(OWNER, 11, 1); int id = orders.create(OWNER, request("qr", 10025000)).id();
        var requests = new ArrayList<Long>();
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            PaymentGateway.Request value = call.getArgument(0); requests.add(value.orderCode());
            if (requests.size() == 1) throw new ApiException(HttpStatus.BAD_GATEWAY, "Simulated timeout");
            return new PaymentGateway.Link(value.orderCode(), value.amount(), "VND", "retry-link", "https://pay.payos.vn/web/retry-link");
        }).when(gateway).create(any());
        assertThatThrownBy(() -> payments.create(OWNER, id)).isInstanceOf(ApiException.class);
        assertThat(payments.create(OWNER, id).status()).isEqualTo("pending");
        assertThat(payments.create(OWNER, id).checkoutUrl()).endsWith("retry-link");
        assertThat(requests).hasSize(2); assertThat(requests.get(0)).isEqualTo(requests.get(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transaction_logs", Integer.class)).isEqualTo(1);
    }

    @Test void callbackCanArriveBeforeCreateResponseWithoutDowngradingPaidStatus() {
        cart.add(OWNER, 11, 1); int id = orders.create(OWNER, request("qr", 10025000)).id();
        var prepared = transactions.prepare(OWNER, id);
        transactions.webhook(new PaymentGateway.Event(prepared.code(), 10025000, "VND", "early-link", "early-ref", "00"));
        var linked = transactions.linked(id, prepared.lease(), new PaymentGateway.Link(prepared.code(), 10025000, "VND", "early-link", "https://pay.payos.vn/web/early-link"));
        assertThat(linked.status()).isEqualTo("paid");
        assertThat(orders.get(OWNER, id).paymentStatus()).isEqualTo("paid");
    }

    @Test void cancellationRequiresProviderConfirmationAndRestoresInventoryOnceThenLateMoneyNeedsReview() throws Exception {
        int id = qr(); long code = code(id);
        when(gateway.cancel(code)).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "Provider unavailable"));
        assertThatThrownBy(() -> payments.cancel(OWNER, id)).isInstanceOf(ApiException.class);
        assertThat(stock()).isEqualTo(1);
        doReturn(new PaymentGateway.State(code, 10025000, 0, "link" + code, "CANCELLED")).when(gateway).cancel(code);
        payments.cancel(OWNER, id); payments.cancel(OWNER, id);
        assertThat(stock()).isEqualTo(2);
        payments.webhook(webhook(code, 10025000, "VND", "link" + code, "late-ref", "00"));
        assertThat(orders.get(OWNER, id).status()).isEqualTo("cancelled");
        assertThat(orders.get(OWNER, id).paymentStatus()).isEqualTo("review");
        assertThat(stock()).isEqualTo(2);
    }

    @Test void providerPaidReconciliationRecoversMissingWebhookAndPartialPaymentKeepsStockHeld() {
        int id = qr(); long code = code(id);
        when(gateway.retrieve(code)).thenReturn(new PaymentGateway.State(code, 10025000, 1, "link" + code, "PENDING"));
        assertThat(payments.refresh(OWNER, id).status()).isEqualTo("review");
        assertThat(stock()).isEqualTo(1);
        when(gateway.cancel(code)).thenReturn(new PaymentGateway.State(code, 10025000, 0, "link" + code, "CANCELLED"));
        payments.cancel(OWNER, id);
        assertThat(orders.get(OWNER, id).paymentStatus()).isEqualTo("review");
        assertThat(stock()).isEqualTo(1);
        when(gateway.retrieve(code)).thenReturn(new PaymentGateway.State(code, 10025000, 10025000, "link" + code, "PAID"));
        assertThat(payments.refresh(OWNER, id).status()).isEqualTo("paid");
    }

    @Test void codAndExpiredUnstartedQrReleaseOnceWithoutCallingGateway() {
        cart.add(OWNER, 11, 1); int cod = orders.create(OWNER, request("cod", 10025000)).id();
        transactions.cancelCod(OWNER, cod); transactions.cancelCod(OWNER, cod); assertThat(stock()).isEqualTo(2);
        cart.add(OWNER, 11, 1); int qr = orders.create(OWNER, request("qr", 10025000)).id();
        jdbc.update("UPDATE orders SET payment_expired_at = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), qr);
        payments.reconcile(); payments.reconcile();
        assertThat(orders.get(OWNER, qr).status()).isEqualTo("cancelled"); assertThat(stock()).isEqualTo(2);
        verify(gateway, never()).create(any());
    }

    @Test void concurrentCheckoutCannotOversellAndConcurrentDuplicateWebhooksCreditOnce() throws Exception {
        User other = users.saveAndFlush(User.create("other", "other@example.invalid", "hash", "Other", false, null));
        deliveryAddress(other.getId());
        jdbc.update("UPDATE product_variants SET quantity = 1 WHERE id = 11");
        cart.add(OWNER, 11, 1); cart.add(other.getId(), 11, 1);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var futures = List.of(OWNER, other.getId()).stream().map(owner -> pool.submit(() -> { start.await(); try { return orders.create(owner, request(owner, "qr", 10025000)); } catch (ApiException expected) { return null; } })).toList();
            start.countDown(); var results = new ArrayList<OrderResponse>();
            for (var future : futures) { var value = future.get(20, TimeUnit.SECONDS); if (value != null) results.add(value); }
            assertThat(results).hasSize(1); assertThat(stock()).isZero();
            var winner = results.getFirst(); UUID owner = jdbc.queryForObject("SELECT user_id FROM orders WHERE id = ?", UUID.class, winner.id());
            payments.create(owner, winner.id()); long code = code(winner.id());
            String body = webhook(code, 10025000, "VND", "link" + code, "concurrent-ref", "00");
            var callbacks = List.of(pool.submit(() -> payments.webhook(body)), pool.submit(() -> payments.webhook(body)));
            for (var callback : callbacks) callback.get(20, TimeUnit.SECONDS);
            assertThat(orders.get(owner, winner.id()).paymentStatus()).isEqualTo("paid");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_events", Integer.class)).isEqualTo(1);
        }
    }

    @Test void concurrentCheckoutRespectsLastVoucherUseAndCancellationFreesQuota() throws Exception {
        User other = users.saveAndFlush(User.create("voucher-other", "voucher-other@example.invalid", "hash", "Other", false, null));
        deliveryAddress(other.getId());
        UUID voucher = UUID.randomUUID();
        jdbc.update("INSERT INTO vouchers(id, code, applied_for, type, value, is_active, start_date, end_date, quantity, max_uses_per_user) VALUES (?, 'LAST100', 'all', 'fixed', 100000, true, now() - interval '1 day', now() + interval '1 day', 1, 1)", voucher);
        UUID promotion = UUID.randomUUID();
        jdbc.update("INSERT INTO promotions(id, code, applied_for, type, value, is_active, start_date, end_date) VALUES (?, 'PROMO10', 'product', 'percent', 10, true, now() - interval '1 day', now() + interval '1 day')", promotion);
        jdbc.update("UPDATE products SET promotion_id = ? WHERE id = 1", promotion);
        cart.add(OWNER, 11, 1); cart.add(other.getId(), 11, 1);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var futures = List.of(OWNER, other.getId()).stream().map(owner -> pool.submit(() -> {
                start.await(); var body = new CreateOrderRequest(UUID.randomUUID(), List.of(11), "LAST100", 8925000L, addressId(owner), "", "cod");
                try { return orders.create(owner, body); } catch (ApiException expected) { assertThat(expected.getMessage()).contains("hết lượt"); return null; }
            })).toList();
            start.countDown(); var winners = new ArrayList<OrderResponse>();
            for (var future : futures) { var value = future.get(20, TimeUnit.SECONDS); if (value != null) winners.add(value); }
            assertThat(winners).hasSize(1); assertThat(stock()).isEqualTo(1);
            var winner = winners.getFirst(); UUID owner = jdbc.queryForObject("SELECT user_id FROM orders WHERE id = ?", UUID.class, winner.id());
            assertThat(winner.promotionDiscount()).isEqualTo(1000000); assertThat(winner.voucherDiscount()).isEqualTo(100000);
            transactions.cancelCod(owner, winner.id());
            UUID remaining = owner.equals(OWNER) ? other.getId() : OWNER;
            var body = new CreateOrderRequest(UUID.randomUUID(), List.of(11), "LAST100", 8925000L, addressId(remaining), "", "cod");
            assertThat(orders.create(remaining, body).total()).isEqualByComparingTo("8925000");
        }
    }

    @Test void orderHttpValidatesRequestAndRequiresCsrfBeforeReservingStock() throws Exception {
        cart.add(OWNER, 11, 1);
        mvc.perform(post("/api/v1/orders").cookie(customer).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("cod", 10025000))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/orders").cookie(customer).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(stock()).isEqualTo(2);
        mvc.perform(post("/api/v1/orders").cookie(customer).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request("cod", 10025000))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(10025000)).andExpect(jsonPath("$.paymentStatus").value("unpaid"));
        assertThat(stock()).isEqualTo(1);
    }

    @Test void concurrentPaymentCreationUsesOneProviderRequestWhileLeaseIsActive() throws Exception {
        cart.add(OWNER, 11, 1); int id = orders.create(OWNER, request("qr", 10025000)).id();
        var entered = new CountDownLatch(1); var finish = new CountDownLatch(1);
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            PaymentGateway.Request request = call.getArgument(0); entered.countDown();
            assertThat(finish.await(10, TimeUnit.SECONDS)).isTrue();
            return new PaymentGateway.Link(request.orderCode(), request.amount(), "VND", "lease-link", "https://pay.payos.vn/web/lease-link");
        }).when(gateway).create(any());
        try (var pool = Executors.newSingleThreadExecutor()) {
            var first = pool.submit(() -> payments.create(OWNER, id));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(payments.create(OWNER, id).status()).isEqualTo("creating");
            } finally { finish.countDown(); }
            assertThat(first.get(15, TimeUnit.SECONDS).status()).isEqualTo("pending");
            verify(gateway, times(1)).create(any());
        }
    }

    private int qr() { cart.add(OWNER, 11, 1); int id = orders.create(OWNER, request("qr", 10025000)).id(); payments.create(OWNER, id); return id; }
    private int stock() { return jdbc.queryForObject("SELECT quantity FROM product_variants WHERE id = 11", Integer.class); }
    private long code(int id) { return jdbc.queryForObject("SELECT gateway_order_code FROM transaction_logs WHERE order_id = ?", Long.class, id); }
    private CreateOrderRequest request(String method, long total) { return request(OWNER, method, total); }
    private CreateOrderRequest request(UUID owner, String method, long total) { return new CreateOrderRequest(UUID.randomUUID(), List.of(11), "", total, addressId(owner), "", method); }
    private int addressId(UUID owner) { return jdbc.queryForObject("SELECT id FROM user_addresses WHERE user_id = ? ORDER BY id LIMIT 1", Integer.class, owner); }
    private void deliveryAddress(UUID owner) {
        jdbc.update("UPDATE users SET phone = '0912345678' WHERE id = ?", owner);
        jdbc.update("INSERT INTO user_addresses (user_id, province_city, ward_commune, street, is_default) VALUES (?, 'TP Hồ Chí Minh', 'Phường 1', '12 Đường A', true)", owner);
    }
    private Cookie cookie(UUID owner) { return new Cookie("accessToken", tokens.issue(users.findById(owner).orElseThrow()).accessToken()); }
    private String webhook(long code, long amount, String currency, String link, String reference, String result) throws Exception {
        var data = new TreeMap<String, Object>(Map.of("orderCode", code, "amount", amount, "currency", currency, "paymentLinkId", link, "reference", reference, "code", result));
        for (String field : List.of("description", "accountNumber", "transactionDateTime", "desc", "counterAccountBankId", "counterAccountBankName", "counterAccountName", "counterAccountNumber", "virtualAccountName", "virtualAccountNumber")) data.put(field, "");
        String canonical = data.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(java.util.stream.Collectors.joining("&"));
        Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(CHECKSUM.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        return json.writeValueAsString(Map.of("code", "00", "desc", "Success", "success", true, "data", data, "signature", signature));
    }
}
