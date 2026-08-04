package com.smartadmin.repository;

import com.smartadmin.entity.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    boolean existsByCode(String code);

    @Query(
            "SELECT r FROM Role r WHERE :keyword = '' OR r.name LIKE CONCAT('%', :keyword, '%') OR r.code LIKE CONCAT('%', :keyword, '%')")
    Page<Role> search(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.id = :id")
    Optional<Role> findByIdWithPermissions(@Param("id") Long id);

    List<Role> findByStatusOrderByIdAsc(Integer status);
}
