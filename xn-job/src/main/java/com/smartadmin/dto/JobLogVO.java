package com.smartadmin.dto;

import com.smartadmin.entity.SysJobLog;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JobLogVO {

    private Long id;
    private Long jobId;
    private String jobName;
    private String jobKey;
    private String invokeTarget;
    private String status;
    private String message;
    private String exceptionInfo;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long costMs;

    public static JobLogVO from(SysJobLog entity) {
        JobLogVO vo = new JobLogVO();
        vo.setId(entity.getId());
        vo.setJobId(entity.getJobId());
        vo.setJobName(entity.getJobName());
        vo.setJobKey(entity.getJobKey());
        vo.setInvokeTarget(entity.getInvokeTarget());
        vo.setStatus(entity.getStatus());
        vo.setMessage(entity.getMessage());
        vo.setExceptionInfo(entity.getExceptionInfo());
        vo.setStartTime(entity.getStartTime());
        vo.setEndTime(entity.getEndTime());
        vo.setCostMs(entity.getCostMs());
        return vo;
    }
}
