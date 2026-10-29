package com.nutee.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CatalogIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach
    void seedCatalog() throws Exception {
        jdbc.execute(Files.readString(Path.of("scripts/seed-catalog.sql")));
    }

    @Test
    void seedIsIdempotentAndMatchesTheExistingSchema() throws Exception {
        jdbc.execute(Files.readString(Path.of("scripts/seed-catalog.sql")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(100);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_variants", Integer.class)).isEqualTo(300);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM brands", Integer.class)).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM categories", Integer.class)).isEqualTo(11);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM (SELECT product_id FROM product_variants
                GROUP BY product_id HAVING count(*) NOT BETWEEN 1 AND 5) invalid
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_images WHERE is_primary", Integer.class)).isEqualTo(100);
    }

    @Test
    void publicReadIgnoresAnExpiredCookieButDoesNotExposeAuthenticationOrAllowWrites() throws Exception {
        mvc.perform(get("/api/v1/products").cookie(new Cookie("accessToken", "invalid")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(100))
                .andExpect(jsonPath("$.content.length()").value(12));
        mvc.perform(get("/api/v1/products/1/variants")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("NTSD0001V01"));
        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("accessToken", "invalid")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/products")).andExpect(status().isForbidden());
    }

    @Test
    void filtersCategoriesRecursivelyAndValidatesSqlInputs() throws Exception {
        int root = jdbc.queryForObject("SELECT id FROM categories WHERE parent IS NULL", Integer.class);
        mvc.perform(get("/api/v1/products").param("category", String.valueOf(root)).param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(100));
        mvc.perform(get("/api/v1/products").param("inStock", "true").param("sort", "price-asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(95));
        mvc.perform(get("/api/v1/products").param("spec.ram", "8 GB"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(23));
        mvc.perform(get("/api/v1/products").param("q", "16 GB"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(11));
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
        jdbc.update("UPDATE products SET is_published = false WHERE id = 1");
        try {
            mvc.perform(get("/api/v1/products/1")).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/products/1/variants")).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/products/1/reviews")).andExpect(status().isNotFound());
        } finally {
            jdbc.update("UPDATE products SET is_published = true WHERE id = 1");
        }
        mvc.perform(get("/api/v1/products/1/reviews")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/products").param("page", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(8))
                .andExpect(jsonPath("$.content.length()").value(4));
    }
}
