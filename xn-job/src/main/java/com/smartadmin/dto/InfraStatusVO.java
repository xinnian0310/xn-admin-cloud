package com.smartadmin.dto;

import lombok.Data;

@Data
public class InfraStatusVO {

    private ComponentStatus redis = new ComponentStatus();
    private ComponentStatus minio = new ComponentStatus();
    private ComponentStatus nacos = new ComponentStatus();
    private ComponentStatus kkfileview = new ComponentStatus();

    /** 本后端进程（能返回本接口即视为 UP） */
    private ComponentStatus backend = new ComponentStatus();

    /** 是否允许一键重启配套组件 */
    private boolean restartEnabled;

    private String projectRoot;
    private String startCommand;

    @Data
    public static class ComponentStatus {
        private String name;
        private boolean enabled;
        private String status;
        private String endpoint;
        private String message;

        /** 是否支持一键重启 */
        private boolean restartable;
    }
}
