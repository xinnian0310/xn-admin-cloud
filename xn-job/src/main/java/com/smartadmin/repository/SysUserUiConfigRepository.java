package com.smartadmin.repository;

import com.smartadmin.entity.SysUserUiConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysUserUiConfigRepository extends JpaRepository<SysUserUiConfig, Long> {

    Optional<SysUserUiConfig> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
