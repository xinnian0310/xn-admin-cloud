package com.smartadmin.repository;

import com.smartadmin.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameIgnoreCase(String username);

    long countByStatus(Integer status);

    java.util.List<User> findByStatus(Integer status);

    long countByRole(String role);

    long countByCreatedAtAfter(LocalDateTime time);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    long countByUnit_Id(Long unitId);

    long countByPost_Id(Long postId);

    default long countByPostId(Long postId) {
        return countByPost_Id(postId);
    }

    /** 近 N 天注册时间原始数据，交给业务层按天分桶（避免 DB 方言差异） */
    @Query("SELECT u.createdAt FROM User u WHERE u.createdAt >= :start")
    List<LocalDateTime> findCreatedAtAfter(@Param("start") LocalDateTime start);

    /** 按个人角色统计人数：[角色名, 人数] */
    @Query(
            "SELECT r.name, COUNT(u) FROM User u JOIN u.roles r GROUP BY r.id, r.name ORDER BY COUNT(u) DESC")
    List<Object[]> countGroupByRole();

    /** 按所属单位统计人数：[单位名, 人数] */
    @Query(
            "SELECT un.name, COUNT(u) FROM User u JOIN u.unit un GROUP BY un.id, un.name ORDER BY COUNT(u) DESC")
    List<Object[]> countGroupByUnit();

    /** SuperAdmin、admin 固定前两位，其余按创建时间倒序。 roleId / unitIds 为空时不过滤。 */
    @Query(
            value =
                    """
                    SELECT u FROM User u
                    LEFT JOIN u.unit unit
                    WHERE u.deletedAt IS NULL
                      AND (:keyword = ''
                       OR u.username LIKE CONCAT('%', :keyword, '%')
                       OR u.nickname LIKE CONCAT('%', :keyword, '%')
                       OR u.email LIKE CONCAT('%', :keyword, '%'))
                      AND (:roleId IS NULL OR EXISTS (
                          SELECT 1 FROM u.roles fr WHERE fr.id = :roleId
                      ) OR EXISTS (
                          SELECT 1 FROM u.unit un JOIN un.roles ur WHERE ur.id = :roleId
                      ))
                      AND (:unitIdsEmpty = true OR unit.id IN :unitIds)
                      AND (:selfUserId IS NULL OR u.id = :selfUserId)
                    ORDER BY CASE
                        WHEN LOWER(u.username) = 'superadmin' THEN 0
                        WHEN LOWER(u.username) = 'admin' THEN 1
                        ELSE 2
                    END ASC, u.createdAt DESC, u.id DESC
                    """,
            countQuery =
                    """
                    SELECT COUNT(u) FROM User u
                    LEFT JOIN u.unit unit
                    WHERE u.deletedAt IS NULL
                      AND (:keyword = ''
                       OR u.username LIKE CONCAT('%', :keyword, '%')
                       OR u.nickname LIKE CONCAT('%', :keyword, '%')
                       OR u.email LIKE CONCAT('%', :keyword, '%'))
                      AND (:roleId IS NULL OR EXISTS (
                          SELECT 1 FROM u.roles fr WHERE fr.id = :roleId
                      ) OR EXISTS (
                          SELECT 1 FROM u.unit un JOIN un.roles ur WHERE ur.id = :roleId
                      ))
                      AND (:unitIdsEmpty = true OR unit.id IN :unitIds)
                      AND (:selfUserId IS NULL OR u.id = :selfUserId)
                    """)
    Page<User> search(
            @Param("keyword") String keyword,
            @Param("roleId") Long roleId,
            @Param("unitIds") List<Long> unitIds,
            @Param("unitIdsEmpty") boolean unitIdsEmpty,
            @Param("selfUserId") Long selfUserId,
            Pageable pageable);

    @Query(
            "SELECT u FROM User u LEFT JOIN FETCH u.roles LEFT JOIN FETCH u.unit LEFT JOIN FETCH u.post WHERE u.username = :username")
    Optional<User> findByUsernameWithRoles(@Param("username") String username);

    @Query(
            "SELECT u FROM User u LEFT JOIN FETCH u.roles LEFT JOIN FETCH u.unit LEFT JOIN FETCH u.post WHERE LOWER(u.username) = LOWER(:username)")
    Optional<User> findByUsernameWithRolesIgnoreCase(@Param("username") String username);

    @Query(
            "SELECT u FROM User u LEFT JOIN FETCH u.roles LEFT JOIN FETCH u.unit LEFT JOIN FETCH u.post WHERE u.id = :id")
    Optional<User> findByIdWithRoles(@Param("id") Long id);

    @Query(
            "SELECT COUNT(DISTINCT u) FROM User u JOIN u.roles r WHERE r.code = 'SUPER_ADMIN' AND u.status = 1 AND u.deletedAt IS NULL")
    long countActiveSuperAdmins();

    @Query("SELECT u.id FROM User u WHERE u.unit.id IN :unitIds")
    List<Long> findIdsByUnitIdIn(@Param("unitIds") List<Long> unitIds);

    @Query("SELECT u.username FROM User u WHERE u.unit.id IN :unitIds")
    List<String> findUsernamesByUnitIdIn(@Param("unitIds") List<Long> unitIds);
}
