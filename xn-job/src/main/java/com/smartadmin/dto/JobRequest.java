package com.smartadmin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JobRequest {

    @NotBlank(message = "任务名称不能为空")
    private String name;

    @NotBlank(message = "任务标识不能为空")
    private String jobKey;

    @NotBlank(message = "Cron 表达式不能为空")
    private String cron;

    @NotBlank(message = "调用目标不能为空")
    private String invokeTarget;

    private Integer status;

    private String remark;

    private Boolean concurrent;

    /** 0默认 1忽略misfire 2补偿执行 3不触发 */
    private String misfirePolicy;
}
