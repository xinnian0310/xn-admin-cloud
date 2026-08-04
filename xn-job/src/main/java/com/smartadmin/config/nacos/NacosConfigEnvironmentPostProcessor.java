package com.smartadmin.config.nacos;

import com.alibaba.nacos.api.config.ConfigService;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

/**
 * 启动早期从 Nacos 拉取配置并注入 Environment。
 *
 * <p>不依赖 Spring Cloud，兼容 Boot 4.1。
 */
public class NacosConfigEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private final Log log;

    public NacosConfigEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(getClass());
    }

    /** Boot 3.x 兼容：无 DeferredLogFactory 时的无参构造 */
    public NacosConfigEnvironmentPostProcessor() {
        this.log = org.apache.commons.logging.LogFactory.getLog(getClass());
    }

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {
        if (!NacosConfigSupport.isConfigEnabled(environment)) {
            return;
        }
        String dataId = NacosConfigSupport.dataId(environment);
        String group = NacosConfigSupport.group(environment);
        boolean failFast = NacosConfigSupport.failFast(environment);
        long timeout = NacosConfigSupport.timeoutMs(environment);
        try {
            ConfigService configService = NacosConfigSupport.createConfigService(environment);
            String content = configService.getConfig(dataId, group, timeout);
            if (!StringUtils.hasText(content)) {
                String msg = "Nacos 配置为空: dataId=" + dataId + ", group=" + group;
                if (failFast) {
                    throw new IllegalStateException(msg);
                }
                log.warn(msg + "，继续使用本地配置");
                return;
            }
            Map<String, Object> map = NacosConfigSupport.parseYamlToMap(content, dataId);
            NacosConfigSupport.applyToEnvironment(environment, map);
            log.info(
                    "已从 Nacos 加载配置: dataId="
                            + dataId
                            + ", group="
                            + group
                            + ", keys="
                            + map.size());
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            if (failFast) {
                throw new IllegalStateException("Nacos 配置中心不可用: " + ex.getMessage(), ex);
            }
            log.warn("Nacos 配置拉取失败，继续使用本地配置: " + ex.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
