package com.bankms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Public demo only (app.demo.reset-on-startup, set by the demo profile): drops every table and
 * re-runs all migrations, demo seed included, before the app starts.
 *
 * <p>The free host stops the app after 15 idle minutes, so each visitor after a quiet spell gets
 * a clean bank with fresh dates, whatever the previous visitor froze, closed or transferred. It
 * also means a changed demo password never locks the next visitor out for long.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.demo.reset-on-startup", havingValue = "true")
public class DemoResetConfig {

    @Bean
    FlywayMigrationStrategy cleanThenMigrate() {
        return flyway -> {
            log.warn("Demo mode: dropping every table and reloading the demo data");
            flyway.clean();
            flyway.migrate();
        };
    }
}
