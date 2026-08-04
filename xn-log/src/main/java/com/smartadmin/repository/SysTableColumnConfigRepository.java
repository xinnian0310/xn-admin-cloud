package com.smartadmin.repository;

import com.smartadmin.entity.SysTableColumnConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysTableColumnConfigRepository extends JpaRepository<SysTableColumnConfig, Long> {

    Optional<SysTableColumnConfig> findByUserIdAndTableKey(Long userId, String tableKey);

    void deleteByUserId(Long userId);
}
