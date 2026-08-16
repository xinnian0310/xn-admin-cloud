package com.smartadmin.config;

import com.smartadmin.dto.AppConfigVO;
import com.smartadmin.entity.SysCfgApp;
import com.smartadmin.entity.SysCfgLogRetention;
import com.smartadmin.entity.SysCfgSensitiveData;
import com.smartadmin.entity.SysCfgSession;
import com.smartadmin.entity.SysCfgUi;
import com.smartadmin.repository.SysCfgAppRepository;
import com.smartadmin.repository.SysCfgLogRetentionRepository;
import com.smartadmin.repository.SysCfgSensitiveDataRepository;
import com.smartadmin.repository.SysCfgSessionRepository;
import com.smartadmin.repository.SysCfgUiRepository;
import com.smartadmin.service.AppConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 系统配置分区种子：触发旧表迁移并补齐空分区默认值。 */
@Slf4j
@Component
@Order(8)
@RequiredArgsConstructor
public class AppConfigInitializer implements CommandLineRunner {

    private final SysCfgAppRepository appRepository;
    private final SysCfgSessionRepository sessionRepository;
    private final SysCfgUiRepository uiRepository;
    private final SysCfgLogRetentionRepository logRetentionRepository;
    private final SysCfgSensitiveDataRepository sensitiveDataRepository;
    private final AppConfigService appConfigService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        try {
            // 触发旧 sys_app_config → 分区分表迁移，并补齐空分区
            appConfigService.getPublic();
        } catch (Exception e) {
            log.warn("系统配置分区初始化/迁移跳过：{}", e.getMessage());
        }
        AppConfigVO defaults = new AppConfigVO();
        if (!appRepository.existsById(1L)) {
            SysCfgApp e = new SysCfgApp();
            e.setId(1L);
            e.setConfigJson(objectMapper.writeValueAsString(defaults.getApp()));
            appRepository.save(e);
        }
        if (!sessionRepository.existsById(1L)) {
            SysCfgSession e = new SysCfgSession();
            e.setId(1L);
            e.setConfigJson(objectMapper.writeValueAsString(defaults.getSession()));
            sessionRepository.save(e);
        }
        if (!uiRepository.existsById(1L)) {
            SysCfgUi e = new SysCfgUi();
            e.setId(1L);
            e.setConfigJson(objectMapper.writeValueAsString(defaults.getUi()));
            uiRepository.save(e);
        }
        if (!logRetentionRepository.existsById(1L)) {
            SysCfgLogRetention e = new SysCfgLogRetention();
            e.setId(1L);
            e.setConfigJson(objectMapper.writeValueAsString(defaults.getLogRetention()));
            logRetentionRepository.save(e);
        }
        if (!sensitiveDataRepository.existsById(1L)) {
            SysCfgSensitiveData e = new SysCfgSensitiveData();
            e.setId(1L);
            e.setConfigJson(objectMapper.writeValueAsString(defaults.getSensitiveData()));
            sensitiveDataRepository.save(e);
        }
    }
}
