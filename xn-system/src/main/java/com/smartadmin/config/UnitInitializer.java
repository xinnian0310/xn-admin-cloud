package com.smartadmin.config;

import com.smartadmin.repository.SysUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 单位默认数据已由 {@link DemoDataInitializer} 统一维护。
 * 保留本类空实现，避免旧文档引用失效。
 */
@Component
@Order(4)
@RequiredArgsConstructor
public class UnitInitializer implements CommandLineRunner {

    private final SysUnitRepository unitRepository;

    @Override
    public void run(String... args) {
        // no-op：组织树在 DemoDataInitializer 中幂等补齐
        unitRepository.count();
    }
}
