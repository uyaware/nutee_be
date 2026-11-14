package com.nutee.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import jakarta.servlet.http.Cookie;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
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
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;

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
                .andExpect(jsonPath("$[0].sku").value("TEST-LAPTOP-8"))
                .andExpect(jsonPath("$[0].imageId").value(11))
                .andExpect(jsonPath("$[0].specifications[0].value").value("8 GB"))
                .andExpect(jsonPath("$[1].specifications[0].value").value("16 GB"));
        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("accessToken", "invalid")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/products")).andExpect(status().isForbidden());
    }

    @Test
    void sortsBySalesAndFiltersOnlyCurrentlyDiscountedPrices() throws Exception {
        jdbc.update("UPDATE products SET sold_count = CASE id WHEN 1 THEN 2 WHEN 2 THEN 12 ELSE 0 END");
        jdbc.update("""
                INSERT INTO promotions (id, code, applied_for, type, value, is_active, start_date, end_date)
                VALUES ('26000000-0000-0000-0000-000000000099', 'TESTSALE', 'product', 'percent', 10,
                    true, now() - interval '1 day', now() + interval '1 day')
                """);
        jdbc.update("UPDATE products SET promotion_id='26000000-0000-0000-0000-000000000099' WHERE id=1");
        entityManager.clear();
        mvc.perform(get("/api/v1/products").param("sort", "best-selling"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(2));
        mvc.perform(get("/api/v1/products").param("discounted", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(1));
        jdbc.update("UPDATE promotions SET end_date=now()-interval '1 hour' WHERE code='TESTSALE'");
        entityManager.clear();
        mvc.perform(get("/api/v1/products").param("discounted", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/products").param("discounted", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void facetsFollowSelectedCategoryAndItsDescendants() throws Exception {
        mvc.perform(get("/api/v1/products/facets").param("category", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brands.length()").value(1))
                .andExpect(jsonPath("$.brands[0].name").value("ExampleTech"))
                .andExpect(jsonPath("$.specifications[?(@.name == 'connection')]").isEmpty())
                .andExpect(jsonPath("$.specifications[?(@.name == 'ram')]").isNotEmpty());
        mvc.perform(get("/api/v1/products/facets").param("category", "103"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brands.length()").value(1))
                .andExpect(jsonPath("$.brands[0].name").value("KeyWorks"))
                .andExpect(jsonPath("$.specifications[?(@.name == 'ram')]").isEmpty());
        mvc.perform(get("/api/v1/products/facets").param("category", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.brands.length()").value(2));
        mvc.perform(get("/api/v1/products/facets").param("category", "99999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.brands.length()").value(0));
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
        mvc.perform(get("/api/v1/products").param("minPrice", "6000000").param("maxPrice", "11000000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(1));
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
                .andExpect(jsonPath("$.defaultVariantId").value(11))
                .andExpect(jsonPath("$.brands.length()").value(1))
                .andExpect(jsonPath("$.brands[0].name").value("ExampleTech"))
                .andExpect(jsonPath("$.specifications.length()").value(4));
        mvc.perform(get("/api/v1/products/facets")).andExpect(status().isOk())
                .andExpect(jsonPath("$.specifications.length()").value(3));
    }

    @Test
    void brandAndCategoryEndpointsKeepTheirIndependentReadContracts() throws Exception {
        mvc.perform(get("/api/v1/brands")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("ExampleTech"));
        mvc.perform(get("/api/v1/brands/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("ExampleTech"));
        mvc.perform(get("/api/v1/brands/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/categories")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
        mvc.perform(get("/api/v1/categories/101")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.name").value("Laptops"))
                .andExpect(jsonPath("$.parentId").value(100));
        mvc.perform(get("/api/v1/categories/100")).andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").isEmpty());
        mvc.perform(get("/api/v1/categories/999999")).andExpect(status().isNotFound());

        jdbc.update("UPDATE brands SET name = 'alpha' WHERE id = 1");
        jdbc.update("UPDATE brands SET name = 'Beta' WHERE id = 2");
        entityManager.clear();
        mvc.perform(get("/api/v1/brands")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("alpha"))
                .andExpect(jsonPath("$[1].name").value("Beta"));
    }

    @Test
    void loadsJpaProductRelationsInBatchesAsThePageGrows() throws Exception {
        jdbc.update("INSERT INTO product_categories (product_id, cat_id) VALUES (1, 102)");
        mvc.perform(get("/api/v1/products/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.categories.length()").value(2));
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        entityManager.clear();
        statistics.clear();
        mvc.perform(get("/api/v1/products").param("size", "1")).andExpect(status().isOk());
        long oneProduct = statistics.getPrepareStatementCount();
        entityManager.clear();
        statistics.clear();
        mvc.perform(get("/api/v1/products").param("size", "3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3));
        long threeProducts = statistics.getPrepareStatementCount();
        assertThat(oneProduct).isPositive();
        assertThat(threeProducts).isLessThanOrEqualTo(oneProduct + 1);
        assertThat(threeProducts).isLessThanOrEqualTo(8);
    }

    @Test
    void categoryFilteringTerminatesEvenWhenTheHierarchyContainsACycle() throws Exception {
        jdbc.update("UPDATE categories SET parent = 101 WHERE id = 100");
        mvc.perform(get("/api/v1/products").param("category", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void searchesProductsWithoutABrandAndTreatsWildcardsAsLiteralText() throws Exception {
        jdbc.update("UPDATE products SET brand_id = NULL, name = ? WHERE id = 3", "Keyboard 100% _\\ edition");
        for (String q : new String[] {"keyboard", "%", "_", "\\"}) {
            mvc.perform(get("/api/v1/products").param("q", q))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(3))
                    .andExpect(jsonPath("$.content[0].brands.length()").value(0));
        }
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
        jdbc.update("UPDATE review_histories SET created_at = '2026-01-01T00:00:00Z' WHERE id = 402");
        jdbc.update("UPDATE users SET full_name = '   ' WHERE username = 'catalog-reviewer'");
        mvc.perform(get("/api/v1/products/1/reviews")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].rating").value(5))
                .andExpect(jsonPath("$.content[0].comment").value("Updated review"))
                .andExpect(jsonPath("$.content[0].authorName").value("catalog-reviewer"));
    }

    @Test
    @Sql(statements = "SELECT 1")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void developmentSeedIncludesCatalogAndBusinessData() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("catalog_seed").defaultSchema("catalog_seed").cleanDisabled(true).load().migrate();
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            connection.setSchema("catalog_seed");
            var dataSource = new SingleConnectionDataSource(connection, true);
            var seeded = new JdbcTemplate(dataSource);
            var seed = new ResourceDatabasePopulator();
            seed.setSqlScriptEncoding("UTF-8");
            seed.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
            seed.addScript(new ClassPathResource("db/seed/seed-local.sql"));
            seed.execute(dataSource);
            assertThat(seeded.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(50);
            assertThat(seeded.queryForObject("SELECT count(*) FROM product_variants", Integer.class)).isEqualTo(150);
            assertThat(seeded.queryForObject("SELECT count(*) FROM categories WHERE parent IS NULL", Integer.class)).isEqualTo(5);
            assertThat(seeded.queryForObject("SELECT count(*) FROM categories WHERE name = '?? ?i?n t?'", Integer.class)).isZero();
            for (String table : new String[] {"users", "orders", "reviews", "aftersale_requests", "promotions", "vouchers"}) {
                assertThat(seeded.queryForObject("SELECT count(*) FROM " + table, Integer.class)).isPositive();
            }
            assertThat(seeded.queryForObject("SELECT count(*) FROM products WHERE default_variant_id IS NULL", Integer.class)).isZero();
            assertThat(seeded.queryForObject("SELECT count(*) FROM orders WHERE user_address_id IS NULL", Integer.class)).isZero();
            assertThat(seeded.queryForObject("SELECT count(*) FROM product_images WHERE image_url LIKE 'https://images.unsplash.com/%'", Integer.class)).isEqualTo(50);

        }
    }
}
