package com.smartadmin.config;

import com.smartadmin.entity.Role;
import com.smartadmin.entity.SysUnit;
import com.smartadmin.entity.User;
import com.smartadmin.repository.RoleRepository;
import com.smartadmin.repository.SysNoticeReceiverRepository;
import com.smartadmin.repository.SysTableColumnConfigRepository;
import com.smartadmin.repository.SysUnitRepository;
import com.smartadmin.repository.UserRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 演示组织树与账号清理（仅 dev）：
 *
 * <pre>
 * 心念科技
 * └── 西安分公司
 *     ├── 研发部门
 *     ├── 市场部门
 *     ├── 测试部门
 *     ├── 财务部门
 *     └── 运维部门
 * </pre>
 *
 * 仅保留超级管理员（SuperAdmin）/ 管理员（admin）账号，其余用户清掉。 生产 / 非 dev 环境不会加载，避免误删业务数据。
 */
@Component
@Profile("dev")
@Order(5)
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    private static final Set<String> KEEP_ROLE_CODES = Set.of("SUPER_ADMIN", "ADMIN", "USER");
    private static final Set<String> KEEP_UNIT_CODES =
            Set.of("XN", "XN_XA", "XN_XA_RD", "XN_XA_MKT", "XN_XA_QA", "XN_XA_FIN", "XN_XA_OPS");
    private static final Set<String> KEEP_USERNAMES = Set.of("SuperAdmin", "admin");

    private final SysUnitRepository unitRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final SysNoticeReceiverRepository noticeReceiverRepository;
    private final SysTableColumnConfigRepository tableColumnConfigRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Map<String, SysUnit> units = ensureOrgTree();
        int removedRoles = purgeExtraRoles();
        if (removedRoles > 0) {
            log.info("[demo-data] 已清理多余角色 {} 个", removedRoles);
        }
        renameBuiltinRoles();
        int purgedUsers = purgeNonAdminUsers();
        if (purgedUsers > 0) {
            log.info("[demo-data] 已清理非管理员用户 {} 个", purgedUsers);
        }
        renameSeedNicknames();
        Role userRole = roleRepository.findByCode("USER").orElse(null);
        bindUnitsToUserRole(units, userRole);
        bindBuiltinUsersToRoot(units.get("XN"));
        int removedUnits = purgeObsoleteUnits(units);
        if (removedUnits > 0) {
            log.info("[demo-data] 已清理冗余单位 {} 个", removedUnits);
        }
    }

    /** 删除非内置三角色 */
    private int purgeExtraRoles() {
        Role userRole = roleRepository.findByCode("USER").orElse(null);
        List<Role> extras =
                roleRepository.findAll().stream()
                        .filter(r -> r.getCode() == null || !KEEP_ROLE_CODES.contains(r.getCode()))
                        .toList();
        if (extras.isEmpty()) {
            return 0;
        }
        Set<Long> extraIds = extras.stream().map(Role::getId).collect(Collectors.toSet());

        for (User bare : userRepository.findAll()) {
            User user = userRepository.findByIdWithRoles(bare.getId()).orElse(bare);
            if (user.getRoles() == null) {
                continue;
            }
            boolean changed = user.getRoles().removeIf(r -> extraIds.contains(r.getId()));
            boolean hasKeep =
                    user.getRoles().stream().anyMatch(r -> KEEP_ROLE_CODES.contains(r.getCode()));
            if (!hasKeep && userRole != null) {
                user.getRoles().add(userRole);
                user.setRole("USER");
                changed = true;
            }
            if (changed) {
                userRepository.save(user);
            }
        }

        for (SysUnit bare : unitRepository.findAll()) {
            SysUnit unit = unitRepository.findByIdWithRoles(bare.getId()).orElse(bare);
            if (unit.getRoles() == null) {
                continue;
            }
            if (unit.getRoles().removeIf(r -> extraIds.contains(r.getId()))) {
                unitRepository.save(unit);
            }
        }

        for (Role extra : extras) {
            roleRepository
                    .findByIdWithPermissions(extra.getId())
                    .ifPresent(
                            role -> {
                                if (role.getPermissions() != null) {
                                    role.getPermissions().clear();
                                }
                                roleRepository.save(role);
                                roleRepository.delete(role);
                            });
        }
        return extras.size();
    }

    /** 内置角色展示名：SUPER_ADMIN=超级管理员，ADMIN=管理员 */
    private void renameBuiltinRoles() {
        roleRepository
                .findByCode("SUPER_ADMIN")
                .ifPresent(
                        role -> {
                            boolean dirty = false;
                            if (!"超级管理员".equals(role.getName())) {
                                role.setName("超级管理员");
                                dirty = true;
                            }
                            if (!"拥有全部权限，系统兜底角色".equals(role.getDescription())) {
                                role.setDescription("拥有全部权限，系统兜底角色");
                                dirty = true;
                            }
                            if (dirty) {
                                roleRepository.save(role);
                            }
                        });
        roleRepository
                .findByCode("ADMIN")
                .ifPresent(
                        role -> {
                            boolean dirty = false;
                            if (!"管理员".equals(role.getName())) {
                                role.setName("管理员");
                                dirty = true;
                            }
                            if (!"日常管理，含用户/角色/权限".equals(role.getDescription())) {
                                role.setDescription("日常管理，含用户/角色/权限");
                                dirty = true;
                            }
                            if (dirty) {
                                roleRepository.save(role);
                            }
                        });
    }

    private Map<String, SysUnit> ensureOrgTree() {
        Map<String, SysUnit> map = new HashMap<>();
        SysUnit root = upsertUnit("XN", "心念科技", null, 0, true, "心念科技");
        map.put("XN", root);

        record Node(String code, String name, String parentCode, int sort, String desc) {}
        List<Node> nodes =
                List.of(
                        new Node("XN_XA", "西安分公司", "XN", 1, "西安分公司"),
                        new Node("XN_XA_RD", "研发部门", "XN_XA", 1, "研发部门"),
                        new Node("XN_XA_MKT", "市场部门", "XN_XA", 2, "市场部门"),
                        new Node("XN_XA_QA", "测试部门", "XN_XA", 3, "测试部门"),
                        new Node("XN_XA_FIN", "财务部门", "XN_XA", 4, "财务部门"),
                        new Node("XN_XA_OPS", "运维部门", "XN_XA", 5, "运维部门"));

        for (Node node : nodes) {
            SysUnit parent = map.get(node.parentCode());
            if (parent == null) {
                parent = unitRepository.findByCode(node.parentCode()).orElse(null);
                if (parent != null) {
                    map.put(node.parentCode(), parent);
                }
            }
            if (parent == null) {
                continue;
            }
            SysUnit unit =
                    upsertUnit(
                            node.code(),
                            node.name(),
                            parent.getId(),
                            node.sort(),
                            false,
                            node.desc());
            map.put(node.code(), unit);
        }
        return map;
    }

    private SysUnit upsertUnit(
            String code, String name, Long parentId, int sort, boolean builtIn, String desc) {
        SysUnit unit = unitRepository.findByCode(code).orElseGet(SysUnit::new);
        boolean isNew = unit.getId() == null;
        unit.setCode(code);
        unit.setName(name);
        unit.setParentId(parentId);
        unit.setSort(sort);
        unit.setStatus(1);
        unit.setDescription(desc);
        if (isNew || Boolean.TRUE.equals(unit.getBuiltIn()) || builtIn) {
            unit.setBuiltIn(builtIn);
        }
        return unitRepository.save(unit);
    }

    private void bindUnitsToUserRole(Map<String, SysUnit> units, Role userRole) {
        if (userRole == null) {
            return;
        }
        for (SysUnit unit : units.values()) {
            if (unit == null || unit.getId() == null) {
                continue;
            }
            SysUnit managed = unitRepository.findByIdWithRoles(unit.getId()).orElse(unit);
            managed.setRoles(new HashSet<>(Set.of(userRole)));
            unitRepository.save(managed);
            units.put(unit.getCode(), managed);
        }
    }

    private void bindBuiltinUsersToRoot(SysUnit root) {
        if (root == null) {
            return;
        }
        for (String username : KEEP_USERNAMES) {
            userRepository
                    .findByUsernameWithRolesIgnoreCase(username)
                    .ifPresent(
                            user -> {
                                user.setUnit(root);
                                userRepository.save(user);
                            });
        }
    }

    /** 种子账号昵称：SuperAdmin=超级管理员，admin=管理员 */
    private void renameSeedNicknames() {
        userRepository
                .findByUsernameIgnoreCase("SuperAdmin")
                .ifPresent(
                        user -> {
                            if (!"超级管理员".equals(user.getNickname())) {
                                user.setNickname("超级管理员");
                                userRepository.save(user);
                            }
                        });
        userRepository
                .findByUsernameIgnoreCase("admin")
                .ifPresent(
                        user -> {
                            if (!"管理员".equals(user.getNickname())) {
                                user.setNickname("管理员");
                                userRepository.save(user);
                            }
                        });
    }

    /** 仅保留 SuperAdmin / admin（或带 SUPER_ADMIN、ADMIN 角色的账号），其余用户删除。 */
    private int purgeNonAdminUsers() {
        int removed = 0;
        for (User bare : List.copyOf(userRepository.findAll())) {
            User user = userRepository.findByIdWithRoles(bare.getId()).orElse(bare);
            if (shouldKeepUser(user)) {
                continue;
            }
            deleteUserCascade(user);
            removed++;
        }
        return removed;
    }

    private boolean shouldKeepUser(User user) {
        if (user.getUsername() != null) {
            for (String keep : KEEP_USERNAMES) {
                if (keep.equalsIgnoreCase(user.getUsername())) {
                    return true;
                }
            }
        }
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return false;
        }
        return user.getRoles().stream()
                .anyMatch(r -> "SUPER_ADMIN".equals(r.getCode()) || "ADMIN".equals(r.getCode()));
    }

    private void deleteUserCascade(User user) {
        Long userId = user.getId();
        noticeReceiverRepository.deleteByUserId(userId);
        tableColumnConfigRepository.deleteByUserId(userId);
        if (user.getRoles() != null) {
            user.getRoles().clear();
        }
        user.setUnit(null);
        userRepository.save(user);
        userRepository.delete(user);
    }

    /** 删除不在新组织树中的旧单位 */
    private int purgeObsoleteUnits(Map<String, SysUnit> keepUnits) {
        Set<Long> keepIds =
                keepUnits.values().stream()
                        .filter(Objects::nonNull)
                        .map(SysUnit::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        List<SysUnit> obsolete =
                unitRepository.findAll().stream()
                        .filter(u -> u.getCode() == null || !KEEP_UNIT_CODES.contains(u.getCode()))
                        .sorted(
                                Comparator.comparing((SysUnit u) -> u.getParentId() == null ? 0 : 1)
                                        .reversed()
                                        .thenComparing(SysUnit::getId, Comparator.reverseOrder()))
                        .toList();

        // 先解绑仍引用旧单位的用户
        for (User bare : userRepository.findAll()) {
            User user = userRepository.findByIdWithRoles(bare.getId()).orElse(bare);
            if (user.getUnit() != null && !keepIds.contains(user.getUnit().getId())) {
                user.setUnit(keepUnits.get("XN"));
                userRepository.save(user);
            }
        }

        int removed = 0;
        // 多轮删除：先删叶子再删父级
        boolean progress = true;
        while (progress) {
            progress = false;
            for (SysUnit bare : List.copyOf(unitRepository.findAll())) {
                if (keepIds.contains(bare.getId()) || KEEP_UNIT_CODES.contains(bare.getCode())) {
                    continue;
                }
                long children = unitRepository.countByParentId(bare.getId());
                if (children > 0) {
                    continue;
                }
                SysUnit unit = unitRepository.findByIdWithRoles(bare.getId()).orElse(bare);
                if (unit.getRoles() != null) {
                    unit.getRoles().clear();
                    unitRepository.save(unit);
                }
                unitRepository.delete(unit);
                removed++;
                progress = true;
            }
        }
        return removed;
    }
}
