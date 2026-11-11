package com.nutee.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutee.backend.security.HmacTokenService;
import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
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
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers @Transactional
class ManagementProductAndUserIT {
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired HmacTokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    User adminUser;
    User staffUser;
    Cookie admin;
    Cookie staff;
    Cookie customer;

    @BeforeEach void setup() {
        adminUser = account("ADMIN");
        staffUser = account("STAFF");
        User customerUser = account("CUSTOMER");
        admin = cookie(adminUser); staff = cookie(staffUser); customer = cookie(customerUser);
    }
    private User account(String role) {
        User user = users.saveAndFlush(User.create("u_" + UUID.randomUUID(), UUID.randomUUID() + "@example.invalid", "hash", "User", false, null));
        jdbc.update("update users set role = ? where id = ?", role, user.getId());
        entityManager.clear();
        return users.findById(user.getId()).orElseThrow();
    }
    private Cookie cookie(User user) { return new Cookie("accessToken", tokens.issue(user).accessToken()); }

    @Test void productAndVariantsPersistWithRoleChecksAndStockConflict() throws Exception {
        String path = "/api/v1/management/products";
        mvc.perform(get(path).cookie(customer)).andExpect(status().isForbidden());
        mvc.perform(post(path).cookie(staff).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        String created = mvc.perform(post(path).cookie(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test product\",\"published\":false,\"categoryIds\":[]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = json.readTree(created).get("id").asInt();
        String saved = mvc.perform(post(path + "/" + id + "/variants").cookie(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"TEST-001\",\"price\":100,\"quantity\":4,\"specifications\":[{\"name\":\"Color\",\"value\":\"Blue\"}]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.variants[0].specifications[0].value").value("Blue"))
                .andReturn().getResponse().getContentAsString();
        JsonNode variant = json.readTree(saved).get("variants").get(0);
        int variantId = variant.get("id").asInt();
        mvc.perform(put(path + "/" + id + "/variants/" + variantId).cookie(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"TEST-001\",\"price\":100,\"quantity\":6,\"expectedQuantity\":3,\"specifications\":[]}"))
                .andExpect(status().isConflict());
        mvc.perform(put(path + "/" + id + "/variants/" + variantId).cookie(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"TEST-001\",\"price\":120,\"quantity\":6,\"expectedQuantity\":4,\"specifications\":[{\"name\":\"Color\",\"value\":\"Red\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.variants[0].quantity").value(6));
        mvc.perform(get(path + "/" + id).cookie(staff)).andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[0].specifications[0].value").value("Red"));
        mvc.perform(delete(path + "/" + id).cookie(staff).with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from products where id = ?", Integer.class, id)).isZero();
    }

    @Test void onlyAdminCanManageUsersAndCannotModifyOwnAccess() throws Exception {
        String path = "/api/v1/management/users";
        mvc.perform(get(path).cookie(staff)).andExpect(status().isForbidden());
        mvc.perform(get(path).cookie(admin)).andExpect(status().isOk());
        String body = "{\"role\":\"customer\",\"isActive\":false}";
        mvc.perform(put(path + "/" + adminUser.getId() + "/access").cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put(path + "/" + staffUser.getId() + "/access").cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("customer"));
        entityManager.flush(); entityManager.clear();
        assertThat(users.findById(staffUser.getId()).orElseThrow().isActive()).isFalse();
        mvc.perform(get("/api/v1/management/products").cookie(staff)).andExpect(status().isForbidden());
    }
}
