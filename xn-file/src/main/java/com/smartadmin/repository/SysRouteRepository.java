package com.smartadmin.repository;

import com.smartadmin.entity.SysRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SysRouteRepository extends JpaRepository<SysRoute, Long> {

    List<SysRoute> findByParentIsNullAndStatusOrderBySortAsc(Integer status);

    Optional<SysRoute> findByPath(String path);

    boolean existsByPath(String path);

    long countByParentId(Long parentId);

    @Query("SELECT r FROM SysRoute r LEFT JOIN FETCH r.parent ORDER BY r.sort ASC")
    List<SysRoute> findAllWithParent();
}
