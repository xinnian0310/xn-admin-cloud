package com.smartadmin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.redis")
public class RedisProperties {

    private boolean enabled = false;
    private String host = "127.0.0.1";
    private int port = 6379;
    private String password = "";
    private int database = 0;
}
