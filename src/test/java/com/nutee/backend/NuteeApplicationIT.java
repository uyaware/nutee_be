package com.nutee.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true", "app.docs-enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class NuteeApplicationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void contextStartsWithJpaAndEntireErdSchema() {
        assertThat(entityManagerFactory.isOpen()).isTrue();
        assertThat(entityManagerFactory.getMetamodel().getEntities()).hasSize(3);
        assertThat(flyway.info().applied()).hasSize(2);
        List<String> tables = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);
        assertThat(tables).hasSize(26).contains("flyway_schema_history", "users", "auth_providers",
                "refresh_sessions", "orders", "order_items", "product_variants", "event_logs");
        flyway.validate();
    }

    @Test
    void flywayMigratesValidatesAndDoesNotReapplyTestFixture() {
        Flyway fixture = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/fixture")
                .schemas("migration_smoke")
                .cleanDisabled(true)
                .load();
        assertThat(fixture.migrate().migrationsExecuted).isEqualTo(1);
        fixture.validate();
        assertThat(fixture.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT description FROM migration_smoke.migration_probe WHERE id = 1", String.class))
                .isEqualTo("Flyway migration executed");
    }

    @Test
    void healthAndReadinessArePublicAndDoNotExposeComponents() throws Exception {
        for (String path : List.of("/api/v1/actuator/health", "/api/v1/actuator/health/liveness",
                "/api/v1/actuator/health/readiness")) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist());
        }
        mvc.perform(get("/api/v1/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void enabledDocumentationExposesBearerSchemeAndSwaggerUi() throws Exception {
        mvc.perform(get("/api/v1/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
        mvc.perform(get("/api/v1/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/api/v1/swagger-ui/index.html"));
        for (String asset : List.of("index.html", "swagger-ui.css", "swagger-ui-bundle.js", "swagger-initializer.js")) {
            mvc.perform(get("/api/v1/swagger-ui/" + asset)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/api/v1/api-docs"));
    }

    @Test
    void passwordEncoderUsesSaltedBcrypt() {
        String first = passwordEncoder.encode("test-only-password");
        String second = passwordEncoder.encode("test-only-password");
        assertThat(first).startsWith("$2a$12$").isNotEqualTo(second);
        assertThat(passwordEncoder.matches("test-only-password", first)).isTrue();
        assertThat(passwordEncoder.matches("wrong-password", first)).isFalse();
    }
}
