package com.smartadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/** 系统配置 · 应用信息分区（单例 JSON）。 */
@Getter
@Setter
@Entity
@Table(name = "sys_cfg_app")
public class SysCfgApp {

    @Id private Long id = 1L;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String configJson;

    @UpdateTimestamp private LocalDateTime updatedAt;
}
