package com.nutee.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
class ManagementTaxonomyIT {
    @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired HmacTokenService tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    Cookie staff;
    Cookie customer;

    @BeforeEach void setup() {
        staff = account("STAFF");
        customer = account("CUSTOMER");
    }

    private Cookie account(String role) {
        User user = users.saveAndFlush(User.create("user_" + UUID.randomUUID(), UUID.randomUUID() + "@example.invalid", "test-hash", "User", false, null));
        jdbc.update("update users set role = ? where id = ?", role, user.getId());
        entityManager.clear();
        return new Cookie("accessToken", tokens.issue(users.findById(user.getId()).orElseThrow()).accessToken());
    }

    @Test void staffCrudAndAccessChecks() throws Exception {
        String path = "/api/v1/management/taxonomy/brands";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).cookie(customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/management/vouchers").cookie(staff)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/management/promotions").cookie(staff)).andExpect(status().isForbidden());
        mvc.perform(post(path).cookie(staff).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Brand A\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).cookie(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Brand A\"}"))
                .andExpect(status().isNoContent());
        Integer id = jdbc.queryForObject("select id from brands where name = 'Brand A'", Integer.class);
        mvc.perform(put(path + "/" + id).cookie(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Brand B\"}"))
                .andExpect(status().isNoContent());
        entityManager.flush();
        assertThat(jdbc.queryForObject("select name from brands where id = ?", String.class, id)).isEqualTo("Brand B");
        mvc.perform(delete(path + "/" + id).cookie(staff).with(csrf())).andExpect(status().isNoContent());
    }

    @Test void categoryCannotBecomeItsOwnDescendant() throws Exception {
        String path = "/api/v1/management/taxonomy/categories";
        mvc.perform(post(path).cookie(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Parent\"}"))
                .andExpect(status().isNoContent());
        Integer parent = jdbc.queryForObject("select id from categories where name = 'Parent'", Integer.class);
        mvc.perform(post(path).cookie(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Child\",\"parentId\":" + parent + "}")).andExpect(status().isNoContent());
        Integer child = jdbc.queryForObject("select id from categories where name = 'Child'", Integer.class);
        mvc.perform(put(path + "/" + parent).cookie(staff).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Parent\",\"parentId\":" + child + "}")).andExpect(status().isBadRequest());
        mvc.perform(delete(path + "/" + parent).cookie(staff).with(csrf())).andExpect(status().isConflict());
    }
}
