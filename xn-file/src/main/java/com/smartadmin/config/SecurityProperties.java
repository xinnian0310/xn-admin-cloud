package com.smartadmin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private Login login = new Login();
    private Cors cors = new Cors();
    private Swagger swagger = new Swagger();

    @Data
    public static class Login {
        /** 连续失败次数阈值 */
        private int maxFailures = 5;
        /** 锁定分钟数 */
        private int lockMinutes = 15;
        /** 同一 IP 每分钟登录请求上限 */
        private int rateLimitPerMinute = 30;
        /** 验证码 TTL 秒 */
        private int captchaTtlSeconds = 120;
    }

@Data
public static class Cors {
    /**
     * 允许的来源，逗号分隔。含 * 表示开发宽松模式（不会与 credentials 同时开启）。
     * 生产请配置具体域名白名单。
     */
    private String allowedOrigins = "*";

    public List<String> allowedOriginList() {
        if (!StringUtils.hasText(allowedOrigins)) {
            return List.of("*");
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}

    @Data
    public static class Swagger {
        /** 是否开放 springdoc / swagger-ui（生产建议 false） */
        private boolean enabled = true;
    }
}
