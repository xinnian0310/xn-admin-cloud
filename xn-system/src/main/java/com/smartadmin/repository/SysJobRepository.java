package com.smartadmin.repository;

import com.smartadmin.entity.SysJob;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysJobRepository extends JpaRepository<SysJob, Long> {

    Optional<SysJob> findByJobKey(String jobKey);

    boolean existsByJobKey(String jobKey);

    List<SysJob> findByStatus(Integer status);

    @Query(
            """
            SELECT j FROM SysJob j
            WHERE (:keyword = ''
               OR j.name LIKE CONCAT('%', :keyword, '%')
               OR j.jobKey LIKE CONCAT('%', :keyword, '%')
               OR j.invokeTarget LIKE CONCAT('%', :keyword, '%'))
              AND (:status IS NULL OR j.status = :status)
            ORDER BY j.id DESC
            """)
    Page<SysJob> search(
            @Param("keyword") String keyword, @Param("status") Integer status, Pageable pageable);
}
