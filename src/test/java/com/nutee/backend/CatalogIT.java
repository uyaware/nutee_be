package com.nutee.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
@Sql("/db/fixture/catalog.sql")
class CatalogIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test
    @Sql(statements = "SELECT 1")
    void freshDatabaseHasNoSeedDataAndReturnsAnEmptyCatalog() throws Exception {
        for (String table : new String[] {"products", "product_variants", "brands", "categories", "users"}) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)).isZero();
        }
        mvc.perform(get("/api/v1/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/products/facets")).andExpect(status().isOk())
                .andExpect(jsonPath("$.brands.length()").value(0))
                .andExpect(jsonPath("$.specifications.length()").value(0));
    }

    @Test
    void publicReadIgnoresAnExpiredCookieButDoesNotExposeAuthenticationOrAllowWrites() throws Exception {
        mvc.perform(get("/api/v1/products").cookie(new Cookie("accessToken", "invalid")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content.length()").value(3));
        mvc.perform(get("/api/v1/products/1/variants")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("TEST-LAPTOP-8"));
        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("accessToken", "invalid")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/products")).andExpect(status().isForbidden());
    }

    @Test
    void filtersCategoriesRecursivelyAndValidatesSqlInputs() throws Exception {
        int root = jdbc.queryForObject("SELECT id FROM categories WHERE parent IS NULL", Integer.class);
        mvc.perform(get("/api/v1/products").param("category", String.valueOf(root)).param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/v1/products").param("inStock", "true").param("sort", "price-asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/products").param("spec.ram", "8 GB"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/products").param("q", "16 GB"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/products").param("brand", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/products").param("q", "ExampleTech"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/products").param("q", "' OR 1=1 --"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/products").param("q", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/products").param("sort", "name; DROP TABLE products"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products").param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    void hiddenProductsCannotBeReadAndReviewsAreNotInvented() throws Exception {
        mvc.perform(get("/api/v1/products/5")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/products/5/variants")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/products/5/reviews")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/products/4")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/products/3/reviews")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/products").param("page", "999").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void specificationFiltersMustMatchTheSameVariant() throws Exception {
        mvc.perform(get("/api/v1/products").param("spec.ram", "8 GB").param("spec.storage", "512 GB"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/products").param("spec.ram", "16 GB").param("spec.storage", "512 GB"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/products/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.brands.length()").value(1))
                .andExpect(jsonPath("$.brands[0].name").value("ExampleTech"))
                .andExpect(jsonPath("$.specifications.length()").value(4));
        mvc.perform(get("/api/v1/products/facets")).andExpect(status().isOk())
                .andExpect(jsonPath("$.specifications.length()").value(3));
    }

    @Test
    void reviewsFollowOrderItemsAndReturnTheLatestRevisionOnce() throws Exception {
        mvc.perform(get("/api/v1/products/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(1));
        mvc.perform(get("/api/v1/products/1/reviews")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].rating").value(5))
                .andExpect(jsonPath("$.content[0].comment").value("Updated review"))
                .andExpect(jsonPath("$.content[0].authorName").value("Test reviewer"))
                .andExpect(jsonPath("$.content[0].email").doesNotExist());
    }
}
