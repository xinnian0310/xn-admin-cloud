package com.smartadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/** 站点联系与捐赠配置（单例，configJson 存完整 JSON）。 */
@Getter
@Setter
@Entity
@Table(name = "sys_site_contact")
public class SysSiteContact {

    /** 固定为 1 的单例主键 */
    @Id private Long id = 1L;

    /** 与前端联系/捐赠结构同构的 JSON */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String configJson;

    @UpdateTimestamp private LocalDateTime updatedAt;
}
