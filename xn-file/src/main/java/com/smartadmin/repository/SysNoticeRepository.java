package com.smartadmin.repository;

import com.smartadmin.entity.NoticeStatus;
import com.smartadmin.entity.SysNotice;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysNoticeRepository extends JpaRepository<SysNotice, Long> {

    long countByStatus(NoticeStatus status);

    List<SysNotice> findTop5ByStatusOrderByPublishedAtDesc(NoticeStatus status);

    @Query(
            value =
                    """
                    SELECT n FROM SysNotice n
                    WHERE (:keyword = ''
                       OR n.title LIKE CONCAT('%', :keyword, '%'))
                      AND (:status IS NULL OR n.status = :status)
                      AND (:ownerUnrestricted = true OR n.publisherId IN :ownerIds)
                    ORDER BY n.id DESC
                    """,
            countQuery =
                    """
                    SELECT COUNT(n) FROM SysNotice n
                    WHERE (:keyword = ''
                       OR n.title LIKE CONCAT('%', :keyword, '%'))
                      AND (:status IS NULL OR n.status = :status)
                      AND (:ownerUnrestricted = true OR n.publisherId IN :ownerIds)
                    """)
    Page<SysNotice> search(
            @Param("keyword") String keyword,
            @Param("status") NoticeStatus status,
            @Param("ownerIds") List<Long> ownerIds,
            @Param("ownerUnrestricted") boolean ownerUnrestricted,
            Pageable pageable);
}
