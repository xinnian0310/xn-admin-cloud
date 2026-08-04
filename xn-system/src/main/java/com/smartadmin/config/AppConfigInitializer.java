package com.smartadmin.config;

import com.smartadmin.dto.AppConfigVO;
import com.smartadmin.entity.SysAppConfig;
import com.smartadmin.repository.SysAppConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 系统配置单例种子：仅在空库时写入与前端 app.ts 一致的默认值，不覆盖已有配置。
 */
@Component
@Order(8)
@RequiredArgsConstructor
public class AppConfigInitializer implements CommandLineRunner {

    private final SysAppConfigRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (repository.existsById(1L)) {
            return;
        }
        SysAppConfig entity = new SysAppConfig();
        entity.setId(1L);
        entity.setConfigJson(objectMapper.writeValueAsString(new AppConfigVO()));
        repository.save(entity);
    }
}
