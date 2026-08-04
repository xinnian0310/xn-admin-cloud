package com.smartadmin.repository;

import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.entity.SysOperLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysOperLogRepository extends JpaRepository<SysOperLog, Long> {

    @Query(
            value =
                    """
                    SELECT o FROM SysOperLog o WHERE
                     (:keyword = '' OR o.title LIKE CONCAT('%', :keyword, '%') OR o.operatorName LIKE CONCAT('%', :keyword, '%'))
                     AND (:businessType IS NULL OR o.businessType = :businessType)
                     AND (:status IS NULL OR o.status = :status)
                     AND (:begin IS NULL OR o.operTime >= :begin)
                     AND (:end IS NULL OR o.operTime <= :end)
                     AND (:usernameUnrestricted = true OR o.operatorName IN :usernames)
                    ORDER BY o.id DESC
                    """,
            countQuery =
                    """
                    SELECT COUNT(o) FROM SysOperLog o WHERE
                     (:keyword = '' OR o.title LIKE CONCAT('%', :keyword, '%') OR o.operatorName LIKE CONCAT('%', :keyword, '%'))
                     AND (:businessType IS NULL OR o.businessType = :businessType)
                     AND (:status IS NULL OR o.status = :status)
                     AND (:begin IS NULL OR o.operTime >= :begin)
                     AND (:end IS NULL OR o.operTime <= :end)
                     AND (:usernameUnrestricted = true OR o.operatorName IN :usernames)
                    """)
    Page<SysOperLog> search(
            @Param("keyword") String keyword,
            @Param("businessType") OperBusinessType businessType,
            @Param("status") Integer status,
            @Param("begin") LocalDateTime begin,
            @Param("end") LocalDateTime end,
            @Param("usernames") List<String> usernames,
            @Param("usernameUnrestricted") boolean usernameUnrestricted,
            Pageable pageable);

    @Query(
            """
            SELECT o FROM SysOperLog o WHERE
             (:keyword = '' OR o.title LIKE CONCAT('%', :keyword, '%') OR o.operatorName LIKE CONCAT('%', :keyword, '%'))
             AND (:businessType IS NULL OR o.businessType = :businessType)
             AND (:status IS NULL OR o.status = :status)
             AND (:begin IS NULL OR o.operTime >= :begin)
             AND (:end IS NULL OR o.operTime <= :end)
             AND (:usernameUnrestricted = true OR o.operatorName IN :usernames)
            ORDER BY o.id DESC
            """)
    List<SysOperLog> searchAll(
            @Param("keyword") String keyword,
            @Param("businessType") OperBusinessType businessType,
            @Param("status") Integer status,
            @Param("begin") LocalDateTime begin,
            @Param("end") LocalDateTime end,
            @Param("usernames") List<String> usernames,
            @Param("usernameUnrestricted") boolean usernameUnrestricted);

    @Modifying
    @Query("DELETE FROM SysOperLog o WHERE o.operTime < :before")
    int deleteByOperTimeBefore(@Param("before") LocalDateTime before);
}
