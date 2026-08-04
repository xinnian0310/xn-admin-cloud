package com.smartadmin.repository;

import com.smartadmin.entity.SysLoginPageConfig;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SysLoginPageConfigRepository extends JpaRepository<SysLoginPageConfig, Long> {

    @Query("SELECT c FROM SysLoginPageConfig c WHERE (:keyword = '' OR c.name LIKE CONCAT('%', :keyword, '%'))"
            + " AND (:status IS NULL OR c.status = :status)")
    Page<SysLoginPageConfig> search(@Param("keyword") String keyword, @Param("status") Integer status, Pageable pageable);

    Optional<SysLoginPageConfig> findFirstByStatus(Integer status);

    boolean existsByName(String name);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE SysLoginPageConfig c SET c.status = 0 WHERE c.status = 1 AND (:excludeId IS NULL OR c.id <> :excludeId)")
    void disableAllExcept(@Param("excludeId") Long excludeId);
}
