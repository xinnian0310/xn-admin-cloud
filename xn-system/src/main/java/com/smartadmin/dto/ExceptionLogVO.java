package com.smartadmin.dto;

import com.smartadmin.entity.SysExceptionLog;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ExceptionLogVO {

    private Long id;
    private String requestMethod;
    private String requestUrl;
    private String method;
    private String className;
    private String exceptionName;
    private String message;
    private String stackTrace;
    private String operatorName;
    private String ip;
    private LocalDateTime createdAt;

    public static ExceptionLogVO from(SysExceptionLog entity) {
        ExceptionLogVO vo = new ExceptionLogVO();
        vo.setId(entity.getId());
        vo.setRequestMethod(entity.getRequestMethod());
        vo.setRequestUrl(entity.getRequestUrl());
        vo.setMethod(entity.getMethod());
        vo.setClassName(entity.getClassName());
        vo.setExceptionName(entity.getExceptionName());
        vo.setMessage(entity.getMessage());
        vo.setStackTrace(entity.getStackTrace());
        vo.setOperatorName(entity.getOperatorName());
        vo.setIp(entity.getIp());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
