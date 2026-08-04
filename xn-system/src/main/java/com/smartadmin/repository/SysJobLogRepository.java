package com.smartadmin.repository;

import com.smartadmin.entity.SysJobLog;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysJobLogRepository extends JpaRepository<SysJobLog, Long> {

    @Query(
            "SELECT l FROM SysJobLog l WHERE (:keyword = '' OR l.jobName LIKE CONCAT('%', :keyword, '%') OR l.jobKey LIKE CONCAT('%', :keyword, '%'))"
                    + " AND (:jobId IS NULL OR l.jobId = :jobId)"
                    + " AND (:status IS NULL OR :status = '' OR l.status = :status)"
                    + " AND (:begin IS NULL OR l.startTime >= :begin)"
                    + " AND (:end IS NULL OR l.startTime <= :end)")
    Page<SysJobLog> search(
            @Param("keyword") String keyword,
            @Param("jobId") Long jobId,
            @Param("status") String status,
            @Param("begin") LocalDateTime begin,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    @Query(
            "SELECT l FROM SysJobLog l WHERE (:keyword = '' OR l.jobName LIKE CONCAT('%', :keyword, '%') OR l.jobKey LIKE CONCAT('%', :keyword, '%'))"
                    + " AND (:jobId IS NULL OR l.jobId = :jobId)"
                    + " AND (:status IS NULL OR :status = '' OR l.status = :status)"
                    + " AND (:begin IS NULL OR l.startTime >= :begin)"
                    + " AND (:end IS NULL OR l.startTime <= :end)"
                    + " ORDER BY l.id DESC")
    List<SysJobLog> searchAll(
            @Param("keyword") String keyword,
            @Param("jobId") Long jobId,
            @Param("status") String status,
            @Param("begin") LocalDateTime begin,
            @Param("end") LocalDateTime end);

    @Modifying
    @Query("DELETE FROM SysJobLog l WHERE l.id IN :ids")
    int deleteByIdIn(@Param("ids") Collection<Long> ids);

    @Modifying
    @Query("DELETE FROM SysJobLog l WHERE l.startTime < :before")
    int deleteByStartTimeBefore(@Param("before") LocalDateTime before);
}
