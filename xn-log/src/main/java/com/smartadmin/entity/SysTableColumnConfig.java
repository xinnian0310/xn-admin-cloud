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

@Getter
@Setter
@Entity
@Table(
        name = "sys_table_column_config",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "table_key"}))
public class SysTableColumnConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 表格唯一标识，如 system:routes */
    @Column(name = "table_key", nullable = false, length = 120)
    private String tableKey;

    /** 列配置 JSON 数组 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String columnsJson;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
