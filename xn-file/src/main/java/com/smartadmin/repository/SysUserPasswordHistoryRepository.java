package com.smartadmin.repository;

import com.smartadmin.entity.SysUserPasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SysUserPasswordHistoryRepository extends JpaRepository<SysUserPasswordHistory, Long> {

    List<SysUserPasswordHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
}
