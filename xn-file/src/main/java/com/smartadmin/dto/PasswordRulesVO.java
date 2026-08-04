package com.smartadmin.dto;

import lombok.Data;

/**
 * 当前生效的密码规则（供个人中心 / 用户表单提示，无需安全策略管理权限）。
 */
@Data
public class PasswordRulesVO {

    private Integer minLength;
    private Integer maxLength;
    private Boolean requireUpper;
    private Boolean requireLower;
    private Boolean requireDigit;
    private Boolean requireSpecial;
    private Integer expireDays;
    private Boolean forceChangeFirst;
    private Integer historyCount;
    /** 可读规则说明 */
    private String tip;
}
