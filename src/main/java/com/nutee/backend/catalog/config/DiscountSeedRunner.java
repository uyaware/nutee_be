package com.nutee.backend.catalog.config;

import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

/** Tạo khuyến mãi và voucher mẫu sau khi catalog local đã sẵn sàng. */
@Component
@Order(2)
@Profile("local")
@ConditionalOnProperty(name = "app.seed.discounts-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DiscountSeedRunner implements ApplicationRunner {
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding("UTF-8");
        populator.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
        populator.addScript(new ClassPathResource("db/seed/seed-discounts.sql"));
        populator.execute(dataSource);
        log.info("Local sample promotions and vouchers are ready.");
    }
}
