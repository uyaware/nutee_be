package com.nutee.backend.catalog.config;

import javax.sql.DataSource;
import jakarta.persistence.EntityManager;
import com.nutee.backend.user.config.LocalAccountSeedRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Tạo catalog mẫu sau Flyway khi chạy local; SQL giữ nguyên dữ liệu mẫu đã được chỉnh sửa. */
@Component
@Order(1)
@Profile("local")
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class CatalogSeedRunner implements ApplicationRunner {
    private final DataSource dataSource;
    private final EntityManager entityManager;
    private final LocalAccountSeedRunner accounts;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext('nutee-local-seed'))::text").getSingleResult();
        boolean populated = (Boolean) entityManager.createNativeQuery("""
                SELECT EXISTS (SELECT 1 FROM users) OR EXISTS (SELECT 1 FROM products)
                    OR EXISTS (SELECT 1 FROM categories) OR EXISTS (SELECT 1 FROM brands)
                    OR EXISTS (SELECT 1 FROM orders) OR EXISTS (SELECT 1 FROM promotions)
                    OR EXISTS (SELECT 1 FROM vouchers) OR EXISTS (SELECT 1 FROM event_logs)
                    OR EXISTS (SELECT 1 FROM transaction_logs)
                """).getSingleResult();
        if (populated) {
            log.info("Local sample data skipped: database already contains data.");
            return;
        }
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding("UTF-8");
        // PostgreSQL DO blocks contain semicolons; execute the existing seed as one script.
        populator.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
        populator.addScript(new ClassPathResource("db/seed/seed-local.sql"));
        populator.execute(dataSource);
        accounts.run(null);
        log.info("Local sample data is ready.");
    }
}
