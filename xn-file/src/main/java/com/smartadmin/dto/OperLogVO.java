package com.smartadmin.dto;

import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.entity.SysOperLog;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OperLogVO {

    private Long id;
    private String title;
    private OperBusinessType businessType;
    private String operatorName;
    private String requestMethod;
    private String requestUrl;
    private String method;
    private String ip;
    private String params;
    private Integer status;
    private String errorMsg;
    private Long costTime;
    private LocalDateTime operTime;

    public static OperLogVO from(SysOperLog entity) {
        OperLogVO vo = new OperLogVO();
        vo.setId(entity.getId());
        vo.setTitle(entity.getTitle());
        vo.setBusinessType(entity.getBusinessType());
        vo.setOperatorName(entity.getOperatorName());
        vo.setRequestMethod(entity.getRequestMethod());
        vo.setRequestUrl(entity.getRequestUrl());
        vo.setMethod(entity.getMethod());
        vo.setIp(entity.getIp());
        vo.setParams(entity.getParams());
        vo.setStatus(entity.getStatus());
        vo.setErrorMsg(entity.getErrorMsg());
        vo.setCostTime(entity.getCostTime());
        vo.setOperTime(entity.getOperTime());
        return vo;
    }
}
