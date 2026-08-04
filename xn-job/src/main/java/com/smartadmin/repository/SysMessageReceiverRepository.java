package com.smartadmin.repository;

import com.smartadmin.entity.SysMessageReceiver;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysMessageReceiverRepository extends JpaRepository<SysMessageReceiver, Long> {

    void deleteByMessageId(Long messageId);

    long countByMessageId(Long messageId);

    long countByMessageIdAndReadAtIsNotNull(Long messageId);

    long countByUserIdAndReadAtIsNull(Long userId);

    Optional<SysMessageReceiver> findByMessageIdAndUserId(Long messageId, Long userId);

    @Query(
            """
            SELECT r FROM SysMessageReceiver r
            WHERE r.messageId = :messageId
            ORDER BY r.readAt DESC NULLS LAST, r.id DESC
            """)
    List<SysMessageReceiver> findReaders(@Param("messageId") Long messageId);

    @Query(
            """
            SELECT r FROM SysMessageReceiver r
            WHERE r.userId = :userId
            ORDER BY r.createdAt DESC
            """)
    List<SysMessageReceiver> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
}
