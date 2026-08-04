package com.smartadmin.dto;

import com.smartadmin.entity.SysJob;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JobVO {

    private Long id;
    private String name;
    private String jobKey;
    private String cron;
    private String invokeTarget;
    private Integer status;
    private String remark;
    private Boolean concurrent;
    private String misfirePolicy;
    private LocalDateTime lastRunAt;
    private String lastStatus;
    private String lastMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static JobVO from(SysJob job) {
        JobVO vo = new JobVO();
        vo.setId(job.getId());
        vo.setName(job.getName());
        vo.setJobKey(job.getJobKey());
        vo.setCron(job.getCron());
        vo.setInvokeTarget(job.getInvokeTarget());
        vo.setStatus(job.getStatus());
        vo.setRemark(job.getRemark());
        vo.setConcurrent(job.getConcurrent());
        vo.setMisfirePolicy(job.getMisfirePolicy());
        vo.setLastRunAt(job.getLastRunAt());
        vo.setLastStatus(job.getLastStatus());
        vo.setLastMessage(job.getLastMessage());
        vo.setCreatedAt(job.getCreatedAt());
        vo.setUpdatedAt(job.getUpdatedAt());
        return vo;
    }
}
