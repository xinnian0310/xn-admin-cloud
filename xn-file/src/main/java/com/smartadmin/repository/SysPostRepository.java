package com.smartadmin.repository;

import com.smartadmin.entity.SysPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SysPostRepository extends JpaRepository<SysPost, Long> {

    boolean existsByCode(String code);

    Optional<SysPost> findByCode(String code);

    @Query("SELECT p FROM SysPost p WHERE (:keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%') OR p.code LIKE CONCAT('%', :keyword, '%'))"
            + " AND (:status IS NULL OR p.status = :status)")
    Page<SysPost> search(@Param("keyword") String keyword, @Param("status") Integer status, Pageable pageable);

    @Query("SELECT p FROM SysPost p WHERE (:keyword = '' OR p.name LIKE CONCAT('%', :keyword, '%') OR p.code LIKE CONCAT('%', :keyword, '%'))"
            + " AND (:status IS NULL OR p.status = :status) ORDER BY p.sort ASC, p.id ASC")
    List<SysPost> searchAll(@Param("keyword") String keyword, @Param("status") Integer status);

    List<SysPost> findByStatusOrderBySortAscIdAsc(Integer status);
}
