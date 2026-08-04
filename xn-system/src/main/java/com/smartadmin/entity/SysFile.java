package com.smartadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 上传文件元数据（对象本体在 MinIO / 本地 uploads，本表存索引信息）。
 */
@Getter
@Setter
@Entity
@Table(
        name = "sys_file",
        indexes = {
                @Index(name = "idx_sys_file_object_key", columnList = "objectKey", unique = true),
                @Index(name = "idx_sys_file_prefix", columnList = "prefix"),
                @Index(name = "idx_sys_file_created", columnList = "createdAt")
        }
)
public class SysFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** MinIO object key 或本地相对路径，如 files/xxx.pdf */
    @Column(nullable = false, length = 500, unique = true)
    private String objectKey;

    /** 所属目录前缀，如 files/docs/ ，根为 "" */
    @Column(length = 500)
    private String prefix;

    /** 原始文件名 */
    @Column(nullable = false, length = 255)
    private String originalName;

    /** 存储侧文件名（通常为 uuid+扩展名） */
    @Column(nullable = false, length = 255)
    private String storedName;

    /** 扩展名，如 .pdf（含点）；无扩展名则为空 */
    @Column(length = 32)
    private String extension;

    /** MIME / Content-Type，如 image/png */
    @Column(length = 128)
    private String contentType;

    /** 字节大小 */
    @Column(nullable = false)
    private Long sizeBytes;

    /** minio | local */
    @Column(nullable = false, length = 16)
    private String storage;

    /** MinIO bucket；本地存储可为空 */
    @Column(length = 128)
    private String bucket;

    /** 可访问 URL（MinIO 公网/内网地址或本地 uploads URL） */
    @Column(length = 1000)
    private String url;

    /** 上传人用户名 */
    @Column(length = 64)
    private String uploader;

    @Column(length = 255)
    private String remark;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /** 软删除时间；非空表示已在回收站 */
    private LocalDateTime deletedAt;
}
