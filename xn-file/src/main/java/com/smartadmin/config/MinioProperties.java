package com.smartadmin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.minio")
public class MinioProperties {

    private boolean enabled = false;

    /** SDK / 内网访问地址 */
    private String endpoint = "http://127.0.0.1:9000";

    /**
     * 浏览器与 kkFileView 使用的公开 endpoint；为空则回退 {@link #endpoint}。 例如 {@code https://minio.example.com}
     * 或经网关映射的公网地址。
     */
    private String publicEndpoint = "";

    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";
    private String bucket = "xn-admin";
    private String region = "us-east-1";

    public String resolvedPublicEndpoint() {
        if (publicEndpoint != null && !publicEndpoint.isBlank()) {
            return publicEndpoint.trim().replaceAll("/+$", "");
        }
        return endpoint == null ? "" : endpoint.replaceAll("/+$", "");
    }
}
