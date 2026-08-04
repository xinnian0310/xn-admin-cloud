package com.smartadmin.repository;

import com.smartadmin.entity.SysExceptionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SysExceptionLogRepository extends JpaRepository<SysExceptionLog, Long> {

    @Query(
            value = """
                    SELECT e FROM SysExceptionLog e
                    WHERE (:keyword = ''
                       OR e.requestUrl LIKE CONCAT('%', :keyword, '%')
                       OR e.exceptionName LIKE CONCAT('%', :keyword, '%')
                       OR e.operatorName LIKE CONCAT('%', :keyword, '%')
                       OR e.message LIKE CONCAT('%', :keyword, '%'))
                      AND (:begin IS NULL OR e.createdAt >= :begin)
                      AND (:end IS NULL OR e.createdAt <= :end)
                      AND (:usernameUnrestricted = true OR e.operatorName IN :usernames)
                    ORDER BY e.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(e) FROM SysExceptionLog e
                    WHERE (:keyword = ''
                       OR e.requestUrl LIKE CONCAT('%', :keyword, '%')
                       OR e.exceptionName LIKE CONCAT('%', :keyword, '%')
                       OR e.operatorName LIKE CONCAT('%', :keyword, '%')
                       OR e.message LIKE CONCAT('%', :keyword, '%'))
                      AND (:begin IS NULL OR e.createdAt >= :begin)
                      AND (:end IS NULL OR e.createdAt <= :end)
                      AND (:usernameUnrestricted = true OR e.operatorName IN :usernames)
                    """
    )
    Page<SysExceptionLog> search(@Param("keyword") String keyword,
                                 @Param("begin") LocalDateTime begin,
                                 @Param("end") LocalDateTime end,
                                 @Param("usernames") List<String> usernames,
                                 @Param("usernameUnrestricted") boolean usernameUnrestricted,
                                 Pageable pageable);

    @Query("""
            SELECT e FROM SysExceptionLog e
            WHERE (:keyword = ''
               OR e.requestUrl LIKE CONCAT('%', :keyword, '%')
               OR e.exceptionName LIKE CONCAT('%', :keyword, '%')
               OR e.operatorName LIKE CONCAT('%', :keyword, '%')
               OR e.message LIKE CONCAT('%', :keyword, '%'))
              AND (:begin IS NULL OR e.createdAt >= :begin)
              AND (:end IS NULL OR e.createdAt <= :end)
              AND (:usernameUnrestricted = true OR e.operatorName IN :usernames)
            ORDER BY e.id DESC
            """)
    List<SysExceptionLog> searchAll(@Param("keyword") String keyword,
                                    @Param("begin") LocalDateTime begin,
                                    @Param("end") LocalDateTime end,
                                    @Param("usernames") List<String> usernames,
                                    @Param("usernameUnrestricted") boolean usernameUnrestricted);

    @Modifying
    @Query("DELETE FROM SysExceptionLog e WHERE e.createdAt < :before")
    int deleteByCreatedAtBefore(@Param("before") LocalDateTime before);
}
