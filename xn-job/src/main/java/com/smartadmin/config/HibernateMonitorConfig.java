package com.smartadmin.config;

import com.smartadmin.monitor.SqlMonitorInspector;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HibernateMonitorConfig {

    @Bean
    public HibernatePropertiesCustomizer sqlMonitorCustomizer(SqlMonitorInspector inspector) {
        return hibernateProperties -> hibernateProperties.put(
                AvailableSettings.STATEMENT_INSPECTOR, inspector);
    }
}
