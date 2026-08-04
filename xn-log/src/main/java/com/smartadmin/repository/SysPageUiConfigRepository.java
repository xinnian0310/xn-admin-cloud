package com.smartadmin.repository;

import com.smartadmin.entity.SysPageUiConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysPageUiConfigRepository extends JpaRepository<SysPageUiConfig, Long> {

    Optional<SysPageUiConfig> findByRoutePath(String routePath);
}
