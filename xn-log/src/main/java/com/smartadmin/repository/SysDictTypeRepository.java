package com.smartadmin.repository;

import com.smartadmin.entity.SysDictType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SysDictTypeRepository extends JpaRepository<SysDictType, Long> {

    boolean existsByType(String type);

    Optional<SysDictType> findByType(String type);

    @Query("SELECT d FROM SysDictType d WHERE (:keyword = '' OR d.name LIKE CONCAT('%', :keyword, '%') OR d.type LIKE CONCAT('%', :keyword, '%'))"
            + " AND (:status IS NULL OR d.status = :status)")
    Page<SysDictType> search(@Param("keyword") String keyword, @Param("status") Integer status, Pageable pageable);

    List<SysDictType> findByStatusOrderByIdAsc(Integer status);
}
