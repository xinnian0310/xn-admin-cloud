package com.smartadmin.repository;

import com.smartadmin.entity.SysPageUiConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysPageUiConfigRepository extends JpaRepository<SysPageUiConfig, Long> {

    Optional<SysPageUiConfig> findByRoutePath(String routePath);
}
