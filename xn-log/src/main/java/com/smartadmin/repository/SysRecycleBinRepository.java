package com.smartadmin.repository;

import com.smartadmin.entity.SysRecycleBin;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysRecycleBinRepository extends JpaRepository<SysRecycleBin, Long> {

    Optional<SysRecycleBin> findByBizTypeAndBizId(String bizType, Long bizId);

    void deleteByBizTypeAndBizId(String bizType, Long bizId);

    @Query(
            "SELECT r FROM SysRecycleBin r WHERE (:keyword = '' OR r.title LIKE CONCAT('%', :keyword, '%') OR r.summary LIKE CONCAT('%', :keyword, '%') OR r.deletedBy LIKE CONCAT('%', :keyword, '%'))"
                    + " AND (:bizType IS NULL OR :bizType = '' OR r.bizType = :bizType)")
    Page<SysRecycleBin> search(
            @Param("keyword") String keyword, @Param("bizType") String bizType, Pageable pageable);
}
