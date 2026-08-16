package com.smartadmin.config;

import com.smartadmin.dto.SiteContactVO;
import com.smartadmin.entity.SysSiteContact;
import com.smartadmin.repository.SysSiteContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 站点联系与捐赠单例种子：仅在空库时写入默认值，不覆盖已有配置。 */
@Component
@Order(9)
@RequiredArgsConstructor
public class SiteContactInitializer implements CommandLineRunner {

    private final SysSiteContactRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (repository.existsById(1L)) {
            return;
        }
        SysSiteContact entity = new SysSiteContact();
        entity.setId(1L);
        entity.setConfigJson(objectMapper.writeValueAsString(SiteContactVO.defaults()));
        repository.save(entity);
    }
}
