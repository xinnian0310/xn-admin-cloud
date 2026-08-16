package com.smartadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/** 系统配置 · 对象存储条目（名字 / 路径）。 */
@Getter
@Setter
@Entity
@Table(
        name = "sys_cfg_storage",
        indexes = {@Index(name = "uk_sys_cfg_storage_name", columnList = "name", unique = true)})
public class SysCfgStorage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 配置名，如 minio */
    @Column(nullable = false, length = 64, unique = true)
    private String name;

    /** 访问前缀，如 http://127.0.0.1:9000/xn-admin/ */
    @Column(nullable = false, length = 1000)
    private String path;

    @UpdateTimestamp private LocalDateTime updatedAt;
}
