package com.smartadmin.repository;

import com.smartadmin.entity.SysUploadSession;
import com.smartadmin.entity.UploadSessionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysUploadSessionRepository extends JpaRepository<SysUploadSession, Long> {

    Optional<SysUploadSession> findByUploadId(String uploadId);

    /**
     * 秒传候选：同指纹且已完成的会话，取最近一条。
     *
     * <p>{@code chunkSize} 传 null 表示指纹与分片大小无关（全量 sha256），不参与比对。
     */
    @Query(
            """
            SELECT s FROM SysUploadSession s
            WHERE s.hashAlgo = :hashAlgo
              AND s.fileHash = :fileHash
              AND s.fileSize = :fileSize
              AND (:chunkSize IS NULL OR s.chunkSize = :chunkSize)
              AND s.status = com.smartadmin.entity.UploadSessionStatus.COMPLETED
            ORDER BY s.id DESC
            """)
    List<SysUploadSession> findCompletedByFingerprint(
            @Param("hashAlgo") String hashAlgo,
            @Param("fileHash") String fileHash,
            @Param("fileSize") Long fileSize,
            @Param("chunkSize") Integer chunkSize);

    /** 续传候选：同指纹、同分片大小、同上传人且仍在上传中的会话，取最近一条 */
    @Query(
            """
            SELECT s FROM SysUploadSession s
            WHERE s.hashAlgo = :hashAlgo
              AND s.fileHash = :fileHash
              AND s.fileSize = :fileSize
              AND s.chunkSize = :chunkSize
              AND s.uploader = :uploader
              AND s.status = com.smartadmin.entity.UploadSessionStatus.UPLOADING
            ORDER BY s.id DESC
            """)
    List<SysUploadSession> findResumableByFingerprint(
            @Param("hashAlgo") String hashAlgo,
            @Param("fileHash") String fileHash,
            @Param("fileSize") Long fileSize,
            @Param("chunkSize") Integer chunkSize,
            @Param("uploader") String uploader);

    /** 清理用：长时间未完成的会话 */
    List<SysUploadSession> findByStatusAndUpdatedAtBefore(
            UploadSessionStatus status, LocalDateTime before);
}
