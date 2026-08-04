package com.smartadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 登录安全策略（单例）。管理员可在「安全策略」页调整，覆盖 yml 默认值。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_security_policy")
public class SysSecurityPolicy {

    @Id
    private Long id = 1L;

    /** 连续失败次数阈值 */
    @Column(nullable = false)
    private Integer maxFailures = 5;

    /** 锁定分钟数 */
    @Column(nullable = false)
    private Integer lockMinutes = 15;

    /** 同一 IP 每分钟登录请求上限 */
    @Column(nullable = false)
    private Integer rateLimitPerMinute = 30;

    /** 验证码有效期（秒） */
    @Column(nullable = false)
    private Integer captchaTtlSeconds = 120;

    /** 密码最小长度 */
    @Column(name = "pwd_min_length", nullable = false)
    private Integer pwdMinLength = 6;

    /** 密码最大长度 */
    @Column(name = "pwd_max_length", nullable = false)
    private Integer pwdMaxLength = 50;

    @Column(name = "pwd_require_upper", nullable = false)
    private Boolean pwdRequireUpper = false;

    @Column(name = "pwd_require_lower", nullable = false)
    private Boolean pwdRequireLower = false;

    @Column(name = "pwd_require_digit", nullable = false)
    private Boolean pwdRequireDigit = false;

    @Column(name = "pwd_require_special", nullable = false)
    private Boolean pwdRequireSpecial = false;

    /** 密码有效天数；0 表示不过期 */
    @Column(name = "pwd_expire_days", nullable = false)
    private Integer pwdExpireDays = 0;

    /** 管理员新建/重置密码后，下次登录强制改密 */
    @Column(name = "pwd_force_change_first", nullable = false)
    private Boolean pwdForceChangeFirst = true;

    /** 禁止复用最近 N 次密码；0 表示不校验历史 */
    @Column(name = "pwd_history_count", nullable = false)
    private Integer pwdHistoryCount = 0;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
