package com.smartadmin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component("appNacosProperties")
@ConfigurationProperties(prefix = "app.nacos")
public class AppNacosProperties {

    /** 进程探活 / 一键重启开关（与配置中心独立） */
    private boolean enabled = false;

    /** 如 127.0.0.1:8849 */
    private String serverAddr = "127.0.0.1:8849";

    private String username = "nacos";
    private String password = "nacos";

    private Config config = new Config();

    @Data
    public static class Config {
        /** 是否从 Nacos 拉取配置并支持热更新 */
        private boolean enabled = false;

        private String namespace = "";
        private String group = "DEFAULT_GROUP";

        /** 默认 dataId；留空则按 spring.application.name + profile，如 xn-job-dev.yml */
        private String dataId = "";

        private boolean refresh = true;

        /** true=拉不到配置则启动失败 */
        private boolean failFast = false;

        private long timeoutMs = 3000;
    }
}
