package com.smartadmin.repository;

import com.smartadmin.entity.SysUserPasswordHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysUserPasswordHistoryRepository
        extends JpaRepository<SysUserPasswordHistory, Long> {

    List<SysUserPasswordHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
}
