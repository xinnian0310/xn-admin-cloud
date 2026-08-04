package com.smartadmin.repository;

import com.smartadmin.entity.Permission;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByCode(String code);

    /** 历史遗留的 SEARCH/TABLE 类型已废弃，统一迁移为表格操作列按钮，避免枚举反序列化失败 */
    @Modifying
    @Transactional
    @Query(
            value =
                    "UPDATE sys_permission SET type = 'TABLE_BUTTON' WHERE type IN ('SEARCH', 'TABLE')",
            nativeQuery = true)
    int migrateLegacySearchAndTableTypes();

    boolean existsByCode(String code);

    List<Permission> findByParentIsNullOrderBySortAsc();

    /** 个人角色 ∪ 所属单位默认角色 对应的权限码 */
    @Query(
            """
            SELECT DISTINCT p.code FROM Permission p JOIN p.roles r
            WHERE r.status = 1 AND (
              EXISTS (SELECT 1 FROM r.users u WHERE u.id = :userId)
              OR EXISTS (
                SELECT 1 FROM User u2 JOIN u2.unit un JOIN un.roles ur
                WHERE u2.id = :userId AND ur.id = r.id
              )
            )
            """)
    Set<String> findPermissionCodesByUserId(Long userId);

    /** 个人角色码 ∪ 所属单位默认角色码 */
    @Query(
            """
            SELECT DISTINCT r.code FROM Role r
            WHERE r.status = 1 AND (
              EXISTS (SELECT 1 FROM r.users u WHERE u.id = :userId)
              OR EXISTS (
                SELECT 1 FROM User u2 JOIN u2.unit un JOIN un.roles ur
                WHERE u2.id = :userId AND ur.id = r.id
              )
            )
            """)
    Set<String> findRoleCodesByUserId(Long userId);

    long countByParentId(Long parentId);

    List<Permission> findByParentIdOrderBySortAsc(Long parentId);
}
