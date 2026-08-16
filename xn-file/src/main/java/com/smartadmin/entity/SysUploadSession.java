package com.smartadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 分片上传会话。用于断点续传（浏览器刷新 / 后端重启后仍能续传）与秒传去重。
 *
 * <p>已上传分片清单不落库，一律以存储侧为准（MinIO listParts / 本地临时目录），避免状态漂移。
 */
@Getter
@Setter
@Entity
@Table(
        name = "sys_upload_session",
        indexes = {
            @Index(
                    name = "idx_sys_upload_session_upload_id",
                    columnList = "uploadId",
                    unique = true),
            @Index(
                    name = "idx_sys_upload_session_fingerprint",
                    columnList = "hashAlgo,fileHash,fileSize"),
            @Index(name = "idx_sys_upload_session_status", columnList = "status")
        })
public class SysUploadSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 对外会话标识（UUID），前端后续所有分片请求都带它 */
    @Column(nullable = false, length = 64, unique = true)
    private String uploadId;

    /** 文件指纹（十六进制小写） */
    @Column(nullable = false, length = 128)
    private String fileHash;

    /**
     * 指纹算法。
     *
     * <ul>
     *   <li>{@code sha256}：全量摘要，与分片大小无关
     *   <li>{@code sha256-tree}：各分片摘要拼接后再摘要，取值依赖分片大小，比对时须连同 {@code chunkSize}
     *   <li>{@code meta}：由文件元信息派生，客户端未读内容，仅可用于续传匹配，不参与秒传
     * </ul>
     */
    @Column(nullable = false, length = 40)
    private String hashAlgo;

    /** 原始文件名（已去除路径） */
    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private Integer chunkSize;

    @Column(nullable = false)
    private Integer totalChunks;

    @Column(length = 128)
    private String contentType;

    /** minio | local */
    @Column(nullable = false, length = 16)
    private String storage;

    /** 目标对象 key（MinIO object key 或本地相对路径），初始化时即确定 */
    @Column(nullable = false, length = 500)
    private String objectKey;

    /** 目标目录前缀，如 2026/08/15/ */
    @Column(length = 500)
    private String prefix;

    /** 存储侧文件名 */
    @Column(nullable = false, length = 255)
    private String storedName;

    @Column(length = 128)
    private String bucket;

    /** MinIO 原生 multipart 的 uploadId；本地存储为空 */
    @Column(length = 255)
    private String storageUploadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UploadSessionStatus status = UploadSessionStatus.UPLOADING;

    /** 合并完成后的可访问 URL */
    @Column(length = 1000)
    private String url;

    @Column(length = 64)
    private String uploader;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp private LocalDateTime updatedAt;

    private LocalDateTime completedAt;
}
