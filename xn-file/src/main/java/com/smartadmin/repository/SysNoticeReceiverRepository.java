package com.smartadmin.repository;

import com.smartadmin.entity.SysNoticeReceiver;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysNoticeReceiverRepository extends JpaRepository<SysNoticeReceiver, Long> {

    Optional<SysNoticeReceiver> findByNoticeIdAndUserId(Long noticeId, Long userId);

    long countByNoticeId(Long noticeId);

    long countByNoticeIdAndReadAtIsNotNull(Long noticeId);

    @Query(
            """
            SELECT r FROM SysNoticeReceiver r
            WHERE r.noticeId = :noticeId AND r.readAt IS NOT NULL
            ORDER BY r.readAt DESC
            """)
    List<SysNoticeReceiver> findReaders(@Param("noticeId") Long noticeId);

    @Query(
            """
            SELECT r FROM SysNoticeReceiver r
            JOIN SysNotice n ON n.id = r.noticeId
            WHERE r.userId = :userId AND n.status = com.smartadmin.entity.NoticeStatus.PUBLISHED
            ORDER BY n.publishedAt DESC, n.id DESC
            """)
    List<SysNoticeReceiver> findPublishedByUserId(@Param("userId") Long userId);

    void deleteByNoticeId(Long noticeId);

    void deleteByUserId(Long userId);
}
