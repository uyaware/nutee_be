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
import java.util.Map;
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
    User customerUser;
    Cookie admin;
    Cookie staff;
    Cookie customer;

    @BeforeEach void setup() {
        adminUser = account("ADMIN");
        staffUser = account("STAFF");
        customerUser = account("CUSTOMER");
        admin = cookie(adminUser); staff = cookie(staffUser); customer = cookie(customerUser);
    }
    private User account(String role) {
        User user = users.saveAndFlush(User.create("u_" + UUID.randomUUID(), UUID.randomUUID() + "@example.invalid", "hash", "User", true, null));
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
        mvc.perform(post(path + "/" + id + "/images").cookie(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"/test-product.jpg\",\"primary\":true}"))
                .andExpect(status().isCreated());
        mvc.perform(get(path).param("q", "Test product").cookie(staff))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].imageUrl").value("/test-product.jpg"))
                .andExpect(jsonPath("$.content[0].name").value("Test product"))
                .andExpect(jsonPath("$.content[0].categories").isArray())
                .andExpect(jsonPath("$.content[0].avgRating").value(0))
                .andExpect(jsonPath("$.content[0].soldCount").value(0));
        String saved = mvc.perform(post(path + "/" + id + "/variants").cookie(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"TEST-001\",\"price\":100,\"quantity\":4,\"specifications\":[{\"name\":\"Color\",\"value\":\"Blue\"}]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.variants[0].specifications[0].value").value("Blue"))
                .andReturn().getResponse().getContentAsString();
        JsonNode variant = json.readTree(saved).get("variants").get(0);
        int variantId = variant.get("id").asInt();
        mvc.perform(put(path + "/" + id + "/default-variant/" + variantId).cookie(staff).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.defaultVariantId").value(variantId));
        mvc.perform(patch(path + "/" + id + "/publication").cookie(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"published\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(true));
        mvc.perform(patch(path + "/" + id + "/publication").cookie(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"published\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.defaultVariantId").value(variantId));
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

    @Test void primaryImageCanBeChangedRepeatedlyAndWhenAddingAnImage() throws Exception {
        String path = "/api/v1/management/products";
        String created = mvc.perform(post(path).cookie(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Image test\",\"published\":false,\"categoryIds\":[]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String imagePath = path + "/" + json.readTree(created).get("id").asInt() + "/images";
        String first = mvc.perform(post(imagePath).cookie(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"/first.jpg\",\"primary\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int firstId = json.readTree(first).get("images").get(0).get("id").asInt();
        String second = mvc.perform(post(imagePath).cookie(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"/second.jpg\",\"primary\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.images[0].primary").value(false))
                .andExpect(jsonPath("$.images[1].primary").value(true)).andReturn().getResponse().getContentAsString();
        int secondId = json.readTree(second).get("images").get(1).get("id").asInt();
        mvc.perform(put(imagePath + "/" + firstId).cookie(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"/first.jpg\",\"primary\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.images[0].primary").value(true))
                .andExpect(jsonPath("$.images[1].primary").value(false));
        mvc.perform(put(imagePath + "/" + secondId).cookie(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"/second.jpg\",\"primary\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.images[0].primary").value(false))
                .andExpect(jsonPath("$.images[1].primary").value(true));
    }

    @Test void adminManagesStaffAndAdminsButNotCustomers() throws Exception {
        String path = "/api/v1/management/users";
        mvc.perform(get(path).cookie(staff)).andExpect(status().isForbidden());
        mvc.perform(get(path).cookie(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.hasItem(staffUser.getId().toString())))
                .andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.hasItem(adminUser.getId().toString())))
                .andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(customerUser.getId().toString()))));
        mvc.perform(get(path + "/" + customerUser.getId()).cookie(admin)).andExpect(status().isNotFound());
        mvc.perform(get(path).param("role", "customer").cookie(admin)).andExpect(status().isBadRequest());
        String body = "{\"role\":\"staff\",\"isActive\":false}";
        mvc.perform(put(path + "/" + adminUser.getId() + "/access").cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put(path + "/" + customerUser.getId() + "/access").cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        mvc.perform(put(path + "/" + staffUser.getId() + "/access").cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"customer\",\"isActive\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(path + "/" + staffUser.getId() + "/access").cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("staff"));
        entityManager.flush(); entityManager.clear();
        assertThat(users.findById(staffUser.getId()).orElseThrow().isActive()).isFalse();
        mvc.perform(get("/api/v1/management/products").cookie(staff)).andExpect(status().isUnauthorized());
    }

    @Test void adminCanCreateOnlyStaffOrAdminAccounts() throws Exception {
        String path = "/api/v1/management/users";
        for (String role : new String[] {"staff", "admin", "customer"}) {
            String body = json.writeValueAsString(Map.of("email", UUID.randomUUID() + "@example.invalid",
                    "name", "New account", "password", "Password123!", "role", role));
            mvc.perform(post(path).cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(role.equals("customer") ? status().isBadRequest() : status().isCreated());
        }
    }

    @Test void adminCanPromoteAndManageAnotherAdmin() throws Exception {
        String path = "/api/v1/management/users/" + staffUser.getId();
        mvc.perform(put(path + "/access").cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"admin\",\"isActive\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("admin"));
        mvc.perform(get(path).cookie(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("admin"));
    }
}
