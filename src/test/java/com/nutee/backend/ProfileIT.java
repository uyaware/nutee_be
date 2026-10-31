package com.nutee.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutee.backend.account.dto.AddressRequest;
import com.nutee.backend.account.service.ProfileService;
import com.nutee.backend.auth.entity.AuthProvider;
import com.nutee.backend.auth.repository.AuthProviderRepository;
import com.nutee.backend.security.HmacTokenService;
import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
class ProfileIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AuthProviderRepository providers;
    @Autowired ProfileService profiles;
    @Autowired HmacTokenService tokens;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    private User owner;
    private Cookie cookie;

    @BeforeEach
    void account() {
        owner = users.saveAndFlush(User.create("profile_" + UUID.randomUUID(), UUID.randomUUID() + "@example.invalid",
                "test-hash", null, false, null));
        cookie = new Cookie("accessToken", tokens.issue(owner).accessToken());
    }

    @Test
    void readsAndUpdatesOnlyEditableProfileFieldsAndReportsGoogleLink() throws Exception {
        mvc.perform(get("/api/v1/account/profile").cookie(cookie)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.googleLinked").value(false))
                .andExpect(jsonPath("$.addresses").isEmpty())
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.user.refreshToken").doesNotExist());
        mvc.perform(put("/api/v1/account/profile").cookie(cookie).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"  Người thử nghiệm  \",\"phone\":\"+84912345678\",\"role\":\"ADMIN\",\"email\":\"forged@example.invalid\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.fullName").value("Người thử nghiệm"))
                .andExpect(jsonPath("$.phone").value("0912345678"))
                .andExpect(jsonPath("$.user.email").value(owner.getEmail()))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));
        mvc.perform(get("/api/v1/auth/me").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Người thử nghiệm"));
        providers.saveAndFlush(new AuthProvider(owner, "GOOGLE", "profile-google-sub"));
        mvc.perform(get("/api/v1/account/profile").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.googleLinked").value(true));
        mvc.perform(put("/api/v1/account/profile").cookie(cookie).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Người dùng\",\"phone\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.phone").isEmpty());
    }

    @Test
    void createsEditsChangesDefaultAndPromotesTheNextAddressAfterDeletion() throws Exception {
        int first = addAddress(false);
        int second = addAddress(true);
        mvc.perform(get("/api/v1/account/profile").cookie(cookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses[0].isDefault").value(false))
                .andExpect(jsonPath("$.addresses[1].isDefault").value(true));
        mvc.perform(put("/api/v1/account/addresses/" + first).cookie(cookie).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(addressJson("Người nhận mới", false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.addresses[0].street").value("Người nhận mới"))
                .andExpect(jsonPath("$.addresses[0].recipient").doesNotExist())
                .andExpect(jsonPath("$.addresses[0].phone").doesNotExist());
        mvc.perform(put("/api/v1/account/addresses/" + first + "/default").cookie(cookie).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.addresses[0].isDefault").value(true))
                .andExpect(jsonPath("$.addresses[1].isDefault").value(false));
        mvc.perform(delete("/api/v1/account/addresses/" + first).cookie(cookie).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.addresses.length()").value(1))
                .andExpect(jsonPath("$.addresses[0].id").value(second))
                .andExpect(jsonPath("$.addresses[0].isDefault").value(true));
        mvc.perform(delete("/api/v1/account/addresses/" + second).cookie(cookie).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.addresses").isEmpty());
    }

    @Test
    void validatesFieldsChecksCsrfAndRejectsCrossAccountAccess() throws Exception {
        int id = addAddress(false);
        User other = users.saveAndFlush(User.create("other_" + UUID.randomUUID(), UUID.randomUUID() + "@example.invalid",
                "test-hash", "Other", false, null));
        Cookie otherCookie = new Cookie("accessToken", tokens.issue(other).accessToken());
        mvc.perform(get("/api/v1/account/profile")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/account/addresses").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content(addressJson("Người nhận", false))).andExpect(status().isForbidden());
        for (String body : List.of("{}", "{\"fullName\":\" \",\"phone\":\"0912345678\"}",
                "{\"fullName\":\"Tên hợp lệ\",\"phone\":\"invalid\"}")) {
            mvc.perform(put("/api/v1/account/profile").cookie(cookie).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/account/addresses").cookie(cookie).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(addressJson("Người nhận", true).replace("Phường 1", " ")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/account/addresses/" + id).cookie(otherCookie).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(addressJson("Người khác", true)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/account/addresses/" + id).cookie(otherCookie).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/account/addresses/" + id + "/default").cookie(otherCookie).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/account/profile").cookie(otherCookie)).andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses").isEmpty());
        jdbc.update("UPDATE users SET is_active = false WHERE id = ?", other.getId());
        entityManager.clear();
        mvc.perform(get("/api/v1/account/profile").cookie(otherCookie)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/account/profile").cookie(otherCookie).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Tên hợp lệ\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void completesProfileAndOptionalAddressInOneRequest() throws Exception {
        String valid = "{\"fullName\":\"Tên hồ sơ\",\"phone\":\"0912345678\",\"address\":"
                + addressJson("Người nhận", true) + "}";
        mvc.perform(put("/api/v1/account/profile").cookie(cookie).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(valid.replace("Phường 1", "")))
                .andExpect(status().isBadRequest());
        assertThat(users.findById(owner.getId()).orElseThrow().getFullName()).isNull();
        mvc.perform(put("/api/v1/account/profile").cookie(cookie).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.fullName").value("Tên hồ sơ"))
                .andExpect(jsonPath("$.addresses[0].isDefault").value(true));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cannotDeleteAnAddressReferencedByAnOrderAndPreservesTheDefault() throws Exception {
        int id = addAddress(true);
        try {
            jdbc.update("INSERT INTO orders (order_code, user_id, user_address_id) VALUES (?, ?, ?)", "PROFTEST01", owner.getId(), id);
            mvc.perform(delete("/api/v1/account/addresses/" + id).cookie(cookie).with(csrf()))
                    .andExpect(status().isConflict());
            var result = profiles.get(owner.getId());
            assertThat(result.addresses()).hasSize(1);
            assertThat(result.addresses().getFirst().isDefault()).isTrue();
        } finally {
            jdbc.update("DELETE FROM orders WHERE user_id = ?", owner.getId());
            jdbc.update("DELETE FROM user_addresses WHERE user_id = ?", owner.getId());
            users.deleteById(owner.getId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentDefaultAddressCreatesAreSerializedOnTheAccountRow() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Void> add = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                profiles.addAddress(owner.getId(), new AddressRequest(
                        "TP Hồ Chí Minh", "Phường 1", "12 Đường A", true));
                return null;
            };
            var first = executor.submit(add);
            var second = executor.submit(add);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            var result = profiles.get(owner.getId());
            assertThat(result.addresses()).hasSize(2);
            assertThat(result.addresses().stream().filter(address -> address.isDefault()).count()).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM user_addresses WHERE user_id = ?", owner.getId());
            users.deleteById(owner.getId());
        }
    }

    private int addAddress(boolean isDefault) throws Exception {
        var result = mvc.perform(post("/api/v1/account/addresses").cookie(cookie).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(addressJson("Người nhận", isDefault)))
                .andExpect(status().isCreated()).andReturn();
        var rows = json.readTree(result.getResponse().getContentAsString()).get("addresses");
        return rows.get(rows.size() - 1).get("id").asInt();
    }

    private String addressJson(String street, boolean isDefault) {
        return "{\"provinceCity\":\"TP Hồ Chí Minh\",\"wardCommune\":\"Phường 1\","
                + "\"street\":\"" + street + "\",\"isDefault\":" + isDefault + "}";
    }
}
