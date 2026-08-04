package com.smartadmin.config.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Nacos 配置拉取与 PropertySource 装配（bootstrap / 热更新共用）。 */
public final class NacosConfigSupport {

    public static final String PROPERTY_SOURCE_NAME = "nacosConfig";

    private NacosConfigSupport() {
    }

    public static boolean isConfigEnabled(ConfigurableEnvironment env) {
        return env.getProperty("app.nacos.config.enabled", Boolean.class, false);
    }

    public static String serverAddr(ConfigurableEnvironment env) {
        return env.getProperty("app.nacos.server-addr", "127.0.0.1:8849");
    }

    public static String username(ConfigurableEnvironment env) {
        return env.getProperty("app.nacos.username", "");
    }

    public static String password(ConfigurableEnvironment env) {
        return env.getProperty("app.nacos.password", "");
    }

    public static String namespace(ConfigurableEnvironment env) {
        return env.getProperty("app.nacos.config.namespace", "");
    }

    public static String group(ConfigurableEnvironment env) {
        return env.getProperty("app.nacos.config.group", "DEFAULT_GROUP");
    }

    public static String dataId(ConfigurableEnvironment env) {
        String dataId = env.getProperty("app.nacos.config.data-id");
        if (StringUtils.hasText(dataId)) {
            return dataId.trim();
        }
        // 优先按 profile：xn-file-dev.yml
        String[] profiles = env.getActiveProfiles();
        String appName = env.getProperty("spring.application.name", "xn-file");
        if (profiles.length > 0 && StringUtils.hasText(profiles[0])) {
            return appName + "-" + profiles[0] + ".yml";
        }
        return appName + ".yml";
    }

    public static boolean refresh(ConfigurableEnvironment env) {
        return env.getProperty("app.nacos.config.refresh", Boolean.class, true);
    }

    public static boolean failFast(ConfigurableEnvironment env) {
        return env.getProperty("app.nacos.config.fail-fast", Boolean.class, false);
    }

    public static long timeoutMs(ConfigurableEnvironment env) {
        return env.getProperty("app.nacos.config.timeout-ms", Long.class, 3000L);
    }

    public static ConfigService createConfigService(ConfigurableEnvironment env) throws Exception {
        Properties props = new Properties();
        props.put(PropertyKeyConst.SERVER_ADDR, serverAddr(env));
        String ns = namespace(env);
        if (StringUtils.hasText(ns)) {
            props.put(PropertyKeyConst.NAMESPACE, ns);
        }
        String user = username(env);
        String pass = password(env);
        if (StringUtils.hasText(user)) {
            props.put(PropertyKeyConst.USERNAME, user);
        }
        if (StringUtils.hasText(pass)) {
            props.put(PropertyKeyConst.PASSWORD, pass);
        }
        return NacosFactory.createConfigService(props);
    }

    public static Map<String, Object> parseYamlToMap(String content, String name) throws Exception {
        if (!StringUtils.hasText(content)) {
            return Collections.emptyMap();
        }
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        Resource resource = new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
        List<PropertySource<?>> loaded = loader.load(name, resource);
        Map<String, Object> flat = new LinkedHashMap<>();
        for (PropertySource<?> ps : loaded) {
            if (ps instanceof EnumerablePropertySource<?> eps) {
                for (String key : eps.getPropertyNames()) {
                    flat.put(key, eps.getProperty(key));
                }
            } else if (ps.getSource() instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    flat.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }
        return flat;
    }

    public static void applyToEnvironment(ConfigurableEnvironment env, Map<String, Object> properties) {
        MutablePropertySources sources = env.getPropertySources();
        MapPropertySource ps = new MapPropertySource(PROPERTY_SOURCE_NAME, new HashMap<>(properties));
        if (sources.contains(PROPERTY_SOURCE_NAME)) {
            sources.replace(PROPERTY_SOURCE_NAME, ps);
        } else {
            // 低于系统环境变量 / 命令行，高于本地 application.yml
            if (sources.contains("systemEnvironment")) {
                sources.addAfter("systemEnvironment", ps);
            } else {
                sources.addFirst(ps);
            }
        }
    }
}
