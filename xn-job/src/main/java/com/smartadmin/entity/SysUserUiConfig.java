package com.smartadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/** 用户个人布局 / 字号偏好（覆盖全局系统配置中的对应字段） */
@Getter
@Setter
@Entity
@Table(
        name = "sys_user_ui_config",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id"}))
public class SysUserUiConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 个人 UI 偏好 JSON */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String configJson;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
