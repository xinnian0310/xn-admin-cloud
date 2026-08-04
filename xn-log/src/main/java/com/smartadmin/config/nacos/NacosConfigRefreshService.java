package com.smartadmin.config.nacos;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.smartadmin.config.AppNacosProperties;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 运行期监听 Nacos 配置变更并刷新 Environment，发布 {@link NacosConfigRefreshedEvent}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NacosConfigRefreshService {

    private final ConfigurableEnvironment environment;
    private final AppNacosProperties AppNacosProperties;
    private final ApplicationEventPublisher eventPublisher;

    private final AtomicReference<ConfigService> configServiceRef = new AtomicReference<>();

    @Getter
    private volatile boolean loaded;
    @Getter
    private volatile String dataId;
    @Getter
    private volatile String group;
    @Getter
    private volatile LocalDateTime lastLoadedAt;
    @Getter
    private volatile String lastMessage = "未启用";
    @Getter
    private volatile int lastKeyCount;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!AppNacosProperties.getConfig().isEnabled()) {
            lastMessage = "配置中心未启用（app.nacos.config.enabled=false）";
            return;
        }
        dataId = NacosConfigSupport.dataId(environment);
        group = NacosConfigSupport.group(environment);
        try {
            ConfigService configService = NacosConfigSupport.createConfigService(environment);
            configServiceRef.set(configService);
            String content = configService.getConfig(dataId, group, AppNacosProperties.getConfig().getTimeoutMs());
            applyContent(content, false);
            if (AppNacosProperties.getConfig().isRefresh()) {
                configService.addListener(dataId, group, new Listener() {
                    @Override
                    public Executor getExecutor() {
                        return null;
                    }

                    @Override
                    public void receiveConfigInfo(String configInfo) {
                        try {
                            applyContent(configInfo, true);
                            log.info("Nacos 配置已热更新: dataId={}, keys={}", dataId, lastKeyCount);
                        } catch (Exception ex) {
                            lastMessage = "热更新失败: " + ex.getMessage();
                            log.warn("Nacos 配置热更新失败: {}", ex.getMessage());
                        }
                    }
                });
                lastMessage = loaded
                        ? "已加载并监听热更新: " + dataId
                        : "已监听热更新（当前内容为空）: " + dataId;
            } else if (loaded) {
                lastMessage = "已加载（热更新关闭）: " + dataId;
            }
        } catch (Exception ex) {
            loaded = false;
            lastMessage = "连接失败: " + ex.getMessage();
            if (AppNacosProperties.getConfig().isFailFast()) {
                throw new IllegalStateException("Nacos 配置中心不可用: " + ex.getMessage(), ex);
            }
            log.warn("Nacos 配置监听未启动: {}", ex.getMessage());
        }
    }

    private void applyContent(String content, boolean publishEvent) throws Exception {
        if (!StringUtils.hasText(content)) {
            loaded = false;
            lastKeyCount = 0;
            lastMessage = "配置内容为空: " + dataId;
            return;
        }
        Map<String, Object> map = NacosConfigSupport.parseYamlToMap(content, dataId);
        NacosConfigSupport.applyToEnvironment(environment, map);
        loaded = true;
        lastKeyCount = map.size();
        lastLoadedAt = LocalDateTime.now();
        lastMessage = "已加载: " + dataId + " (" + lastKeyCount + " keys)";
        if (publishEvent) {
            eventPublisher.publishEvent(new NacosConfigRefreshedEvent(this, dataId, group, map.keySet()));
        }
    }

    @PreDestroy
    public void destroy() {
        ConfigService cs = configServiceRef.getAndSet(null);
        if (cs != null) {
            try {
                cs.shutDown();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }
}
