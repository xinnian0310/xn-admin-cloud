package com.smartadmin.config.nacos;

import com.smartadmin.config.InfraProperties;
import com.smartadmin.config.KkFileViewProperties;
import com.smartadmin.config.MinioProperties;
import com.smartadmin.config.AppNacosProperties;
import com.smartadmin.config.RedisProperties;
import com.smartadmin.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 热更新后重新绑定常见 @ConfigurationProperties（无需 Spring Cloud）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NacosConfigPropertiesRebinder {

    private final Environment environment;
    private final RedisProperties redisProperties;
    private final MinioProperties minioProperties;
    private final AppNacosProperties AppNacosProperties;
    private final KkFileViewProperties kkFileViewProperties;
    private final InfraProperties infraProperties;
    private final SecurityProperties securityProperties;

    @EventListener(NacosConfigRefreshedEvent.class)
    public void onRefreshed(NacosConfigRefreshedEvent event) {
        Binder binder = Binder.get(environment);
        rebind(binder, "app.redis", redisProperties);
        rebind(binder, "app.minio", minioProperties);
        rebind(binder, "app.nacos", AppNacosProperties);
        rebind(binder, "app.kkfileview", kkFileViewProperties);
        rebind(binder, "app.infra", infraProperties);
        rebind(binder, "app.security", securityProperties);
        log.info("已重绑 ConfigurationProperties，变更 keys≈{}", event.getKeys().size());
    }

    private <T> void rebind(Binder binder, String prefix, T target) {
        binder.bind(prefix, Bindable.ofInstance(target));
    }
}
