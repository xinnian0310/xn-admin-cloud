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
 * 应用系统配置（单例，与前端 app.ts 结构对应，configJson 存完整 JSON）。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_app_config")
public class SysAppConfig {

    /** 固定为 1 的单例主键 */
    @Id
    private Long id = 1L;

    /** 与前端 AppConfig 同构的 JSON */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String configJson;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
