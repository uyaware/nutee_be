package com.nutee.backend.catalog.config;

import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

/** Tạo catalog mẫu sau Flyway khi chạy local; SQL giữ nguyên dữ liệu mẫu đã được chỉnh sửa. */
@Component
@Profile("local")
@ConditionalOnProperty(name = "app.seed.catalog-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CatalogSeedRunner implements ApplicationRunner {
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding("UTF-8");
        // PostgreSQL DO blocks contain semicolons; execute the existing seed as one script.
        populator.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
        populator.addScript(new ClassPathResource("db/seed/seed-catalog.sql"));
        populator.execute(dataSource);
        log.info("Local sample catalog is ready.");
    }
}
