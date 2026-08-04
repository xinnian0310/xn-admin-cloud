package com.smartadmin.repository;

import com.smartadmin.entity.SysTableColumnConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysTableColumnConfigRepository extends JpaRepository<SysTableColumnConfig, Long> {

    Optional<SysTableColumnConfig> findByUserIdAndTableKey(Long userId, String tableKey);

    void deleteByUserId(Long userId);
}
