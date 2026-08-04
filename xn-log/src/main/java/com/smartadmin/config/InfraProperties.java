package com.smartadmin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.infra")
public class InfraProperties {

    /** 是否允许通过接口一键重启配套组件（仅本机开发建议开启） */
    private boolean restartEnabled = true;

    /** 仓库根目录（含 启动.bat / tool/）。 为空时自动探测：user.dir 或其上一级。 */
    private String projectRoot = "";
}
