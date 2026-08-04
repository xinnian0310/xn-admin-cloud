package com.smartadmin.dto;

import com.smartadmin.entity.SysLoginPageConfig;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginPageConfigVO {

    private Long id;
    private String name;
    private String backgroundUrl;
    private String backgroundFit;
    private Double boxX;
    private Double boxY;
    private Boolean captchaEnabled;
    private String captchaType;
    private Integer status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LoginPageConfigVO from(SysLoginPageConfig entity) {
        LoginPageConfigVO vo = new LoginPageConfigVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setBackgroundUrl(entity.getBackgroundUrl());
        vo.setBackgroundFit(entity.getBackgroundFit() != null ? entity.getBackgroundFit() : "COVER");
        vo.setBoxX(entity.getBoxX());
        vo.setBoxY(entity.getBoxY());
        vo.setCaptchaEnabled(entity.getCaptchaEnabled());
        vo.setCaptchaType(entity.getCaptchaType());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
