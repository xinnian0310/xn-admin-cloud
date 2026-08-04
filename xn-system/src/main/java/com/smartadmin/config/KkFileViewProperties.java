package com.smartadmin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.kkfileview")
public class KkFileViewProperties {

    private boolean enabled = false;

    /** 如 http://127.0.0.1:8012 */
    private String baseUrl = "http://127.0.0.1:8012";
}
