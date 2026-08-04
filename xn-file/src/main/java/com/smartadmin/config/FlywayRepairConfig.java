package com.smartadmin.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 开发环境：已执行过的 migration 若被改动导致 checksum 不一致，启动前 repair 再 migrate。
 * 生产请勿开启（仅 {@code spring.profiles.active=dev} 生效）。
 */
@Configuration
@Profile("dev")
public class FlywayRepairConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.flyway.repair-on-migrate", havingValue = "true", matchIfMissing = true)
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
