package com.smartadmin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SecurityPolicyVO {

    @NotNull
    @Min(1)
    @Max(50)
    private Integer maxFailures;

    @NotNull
    @Min(1)
    @Max(1440)
    private Integer lockMinutes;

    @NotNull
    @Min(1)
    @Max(1000)
    private Integer rateLimitPerMinute;

    @NotNull
    @Min(30)
    @Max(600)
    private Integer captchaTtlSeconds;

    @NotNull
    @Min(6)
    @Max(50)
    private Integer pwdMinLength;

    @NotNull
    @Min(6)
    @Max(50)
    private Integer pwdMaxLength;

    @NotNull
    private Boolean pwdRequireUpper;

    @NotNull
    private Boolean pwdRequireLower;

    @NotNull
    private Boolean pwdRequireDigit;

    @NotNull
    private Boolean pwdRequireSpecial;

    /** 0 = 不过期 */
    @NotNull
    @Min(0)
    @Max(3650)
    private Integer pwdExpireDays;

    @NotNull
    private Boolean pwdForceChangeFirst;

    /** 0 = 不校验历史 */
    @NotNull
    @Min(0)
    @Max(20)
    private Integer pwdHistoryCount;

    /** 只读：最近更新时间 */
    private String updatedAt;
}
