package com.smartadmin.dto;

import com.smartadmin.entity.SysLoginPageConfig;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class LoginPageConfigVO {

    private Long id;
    private String name;
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
        vo.setCaptchaEnabled(entity.getCaptchaEnabled());
        vo.setCaptchaType(entity.getCaptchaType());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
