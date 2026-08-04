package com.smartadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "sys_recycle_bin")
public class SysRecycleBin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** USER | FILE */
    @Column(nullable = false, length = 20)
    private String bizType;

    @Column(nullable = false)
    private Long bizId;

    @Column(nullable = false, length = 200)
    private String title;

    /** 补充说明，如用户名、文件路径 */
    @Column(length = 500)
    private String summary;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String snapshot;

    @Column(length = 50)
    private String deletedBy;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime deletedAt;
}
