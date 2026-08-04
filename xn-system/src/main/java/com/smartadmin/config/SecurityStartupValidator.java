package com.smartadmin.config;

import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 生产环境启动强校验：关键密钥缺失则直接失败，避免默默使用空值。 */
@Component
public class SecurityStartupValidator implements ApplicationRunner {

    private final Environment environment;

    public SecurityStartupValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean prod =
                Arrays.stream(environment.getActiveProfiles())
                        .anyMatch(p -> "prod".equalsIgnoreCase(p));
        if (!prod) {
            return;
        }
        require("app.jwt.secret", "JWT_SECRET");
        require("spring.datasource.password", "DB_PASSWORD");
        String secret = environment.getProperty("app.jwt.secret", "");
        if (secret.length() < 32) {
            throw new IllegalStateException("生产环境 app.jwt.secret（JWT_SECRET）长度须至少 32 字符");
        }
    }

    private void require(String property, String envHint) {
        String value = environment.getProperty(property);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(
                    "生产环境缺少必填配置 " + property + "，请通过环境变量 " + envHint + " 注入");
        }
    }
}
