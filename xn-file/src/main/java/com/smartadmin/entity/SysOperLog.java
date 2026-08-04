package com.smartadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@Entity
@Table(name = "sys_oper_log")
public class SysOperLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 模块标题，如“用户管理” */
    @Column(nullable = false, length = 50)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OperBusinessType businessType = OperBusinessType.OTHER;

    @Column(length = 50)
    private String operatorName;

    @Column(length = 10)
    private String requestMethod;

    @Column(length = 200)
    private String requestUrl;

    /** 类#方法，如 com.smartadmin.controller.UserController#create */
    @Column(length = 200)
    private String method;

    @Column(length = 50)
    private String ip;

    /** 请求入参 JSON（已截断），敏感字段已脱敏 */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String params;

    /** 1 成功 / 0 失败 */
    @Column(nullable = false)
    private Integer status = 1;

    @Column(length = 500)
    private String errorMsg;

    /** 方法执行耗时（毫秒） */
    private Long costTime;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime operTime;
}
