package com.smartadmin.config;

import com.smartadmin.repository.SysUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 单位默认数据由 {@link DemoDataInitializer}（仅 dev）维护；非 dev 环境请在系统中自行维护组织树。 */
@Component
@Order(4)
@RequiredArgsConstructor
public class UnitInitializer implements CommandLineRunner {

    private final SysUnitRepository unitRepository;

    @Override
    public void run(String... args) {
        unitRepository.count();
    }
}
