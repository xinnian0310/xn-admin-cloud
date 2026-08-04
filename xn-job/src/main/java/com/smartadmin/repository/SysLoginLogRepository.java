package com.smartadmin.repository;

import com.smartadmin.entity.SysLoginLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SysLoginLogRepository extends JpaRepository<SysLoginLog, Long> {

    @Query(
            value = """
                    SELECT l FROM SysLoginLog l WHERE
                     (:keyword = '' OR l.username LIKE CONCAT('%', :keyword, '%'))
                     AND (:status IS NULL OR l.status = :status)
                     AND (:begin IS NULL OR l.loginTime >= :begin)
                     AND (:end IS NULL OR l.loginTime <= :end)
                     AND (:usernameUnrestricted = true OR l.username IN :usernames)
                    ORDER BY l.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(l) FROM SysLoginLog l WHERE
                     (:keyword = '' OR l.username LIKE CONCAT('%', :keyword, '%'))
                     AND (:status IS NULL OR l.status = :status)
                     AND (:begin IS NULL OR l.loginTime >= :begin)
                     AND (:end IS NULL OR l.loginTime <= :end)
                     AND (:usernameUnrestricted = true OR l.username IN :usernames)
                    """
    )
    Page<SysLoginLog> search(
            @Param("keyword") String keyword,
            @Param("status") Integer status,
            @Param("begin") LocalDateTime begin,
            @Param("end") LocalDateTime end,
            @Param("usernames") List<String> usernames,
            @Param("usernameUnrestricted") boolean usernameUnrestricted,
            Pageable pageable);

    @Query("""
            SELECT l FROM SysLoginLog l WHERE
             (:keyword = '' OR l.username LIKE CONCAT('%', :keyword, '%'))
             AND (:status IS NULL OR l.status = :status)
             AND (:begin IS NULL OR l.loginTime >= :begin)
             AND (:end IS NULL OR l.loginTime <= :end)
             AND (:usernameUnrestricted = true OR l.username IN :usernames)
            ORDER BY l.id DESC
            """)
    List<SysLoginLog> searchAll(
            @Param("keyword") String keyword,
            @Param("status") Integer status,
            @Param("begin") LocalDateTime begin,
            @Param("end") LocalDateTime end,
            @Param("usernames") List<String> usernames,
            @Param("usernameUnrestricted") boolean usernameUnrestricted);

    @Modifying
    @Query("DELETE FROM SysLoginLog l WHERE l.loginTime < :before")
    int deleteByLoginTimeBefore(@Param("before") LocalDateTime before);
}
