package com.smartadmin.repository;

import com.smartadmin.entity.SysUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SysUnitRepository extends JpaRepository<SysUnit, Long> {

    boolean existsByCode(String code);

    Optional<SysUnit> findByCode(String code);

    long countByParentId(Long parentId);

    List<SysUnit> findAllByOrderBySortAscIdAsc();

    List<SysUnit> findByStatusOrderBySortAscIdAsc(Integer status);

    @Query("SELECT u FROM SysUnit u LEFT JOIN FETCH u.roles WHERE u.id = :id")
    Optional<SysUnit> findByIdWithRoles(@Param("id") Long id);

    @Query("SELECT DISTINCT u FROM SysUnit u LEFT JOIN FETCH u.roles")
    List<SysUnit> findAllWithRoles();

    @Query("SELECT DISTINCT u FROM SysUnit u LEFT JOIN FETCH u.roles WHERE u.status = :status")
    List<SysUnit> findByStatusWithRoles(@Param("status") Integer status);
}
