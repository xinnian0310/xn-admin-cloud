package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.entity.Role;
import com.smartadmin.entity.User;
import com.smartadmin.repository.PermissionRepository;
import com.smartadmin.repository.RoleRepository;
import com.smartadmin.repository.SysUnitRepository;
import com.smartadmin.repository.UserRepository;
import com.smartadmin.security.LoginUser;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RbacService {

    public static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final SysUnitRepository unitRepository;
    private final AppCacheService appCacheService;

    public User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException(401, "未登录");
        }
        User user = null;
        Object principal = auth.getPrincipal();
        if (principal instanceof LoginUser loginUser && loginUser.getId() != null) {
            user = userRepository.findByIdWithRoles(loginUser.getId()).orElse(null);
        }
        if (user == null && auth.getName() != null) {
            user = userRepository.findByUsernameWithRolesIgnoreCase(auth.getName()).orElse(null);
        }
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getDeletedAt() != null) {
            throw new BusinessException(401, "账号已被删除");
        }
        return user;
    }

    public boolean isSuperAdmin(User user) {
        return getRoleCodes(user).contains(SUPER_ADMIN_CODE);
    }

    public boolean isSuperAdmin() {
        return isSuperAdmin(currentUser());
    }

    public boolean hasPermission(String permissionCode) {
        if (isSuperAdmin()) {
            return true;
        }
        User user = currentUser();
        return getPermissionCodes(user).contains(permissionCode);
    }

    public void checkPermission(String permissionCode) {
        if (!hasPermission(permissionCode)) {
            throw new BusinessException(403, "无权限");
        }
    }

    /** 生效角色码 = 个人角色 ∪ 单位默认角色 */
    public List<String> getRoleCodes(User user) {
        return permissionRepository.findRoleCodesByUserId(user.getId()).stream().sorted().toList();
    }

    public List<String> getPermissionCodes(User user) {
        if (isSuperAdmin(user)) {
            return permissionRepository.findAll().stream().map(p -> p.getCode()).sorted().toList();
        }
        return appCacheService.getPermissionCodes(
                user.getId(),
                () ->
                        permissionRepository.findPermissionCodesByUserId(user.getId()).stream()
                                .sorted()
                                .toList());
    }

    public void validateRoleAssignment(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        if (isSuperAdmin()) {
            return;
        }
        Role superAdminRole =
                roleRepository
                        .findByCode(SUPER_ADMIN_CODE)
                        .orElseThrow(() -> new BusinessException("超级管理员角色未初始化"));
        if (roleIds.contains(superAdminRole.getId())) {
            throw new BusinessException(403, "无权分配超级管理员角色");
        }
    }

    /** 用户须有个人角色，或归属到已绑定默认角色的单位 */
    public void validateUserEffectiveRoles(List<Long> roleIds, Long unitId) {
        boolean hasDirect = roleIds != null && !roleIds.isEmpty();
        boolean hasUnitRoles = false;
        if (unitId != null) {
            hasUnitRoles =
                    unitRepository
                            .findByIdWithRoles(unitId)
                            .map(u -> u.getRoles() != null && !u.getRoles().isEmpty())
                            .orElse(false);
        }
        if (!hasDirect && !hasUnitRoles) {
            throw new BusinessException("请选择角色，或将用户归属到已绑定默认角色的单位");
        }
        validateRoleAssignment(roleIds);
    }

    public Set<Role> loadRolesByIds(List<Long> roleIds) {
        List<Role> roles = roleRepository.findAllById(roleIds);
        if (roles.size() != new HashSet<>(roleIds).size()) {
            throw new BusinessException("存在无效的角色");
        }
        return new HashSet<>(roles);
    }

    public void syncLegacyRoleField(User user) {
        String primary =
                user.getRoles().stream()
                        .map(Role::getCode)
                        .min(Comparator.comparing(this::rolePriority))
                        .orElse("USER");
        user.setRole(SUPER_ADMIN_CODE.equals(primary) ? "ADMIN" : primary);
    }

    private int rolePriority(String code) {
        if (SUPER_ADMIN_CODE.equals(code)) {
            return 0;
        }
        if ("ADMIN".equals(code)) {
            return 1;
        }
        if ("USER".equals(code)) {
            return 2;
        }
        if ("GUEST".equals(code)) {
            return 3;
        }
        return 4;
    }

    public void ensureSuperAdminExists(User user, List<Long> newRoleIds) {
        Role superAdminRole = roleRepository.findByCode(SUPER_ADMIN_CODE).orElse(null);
        if (superAdminRole == null) {
            return;
        }
        boolean wasSuperAdmin =
                user.getRoles().stream().anyMatch(r -> SUPER_ADMIN_CODE.equals(r.getCode()));
        boolean willBeSuperAdmin = newRoleIds.contains(superAdminRole.getId());

        if (wasSuperAdmin && !willBeSuperAdmin) {
            long count = userRepository.countActiveSuperAdmins();
            if (count <= 1) {
                throw new BusinessException("不能移除最后一个超级管理员的角色");
            }
        }
    }

    public void ensureCanDisableSuperAdmin(User user, Integer status) {
        if (status != 0) {
            return;
        }
        if (isSuperAdmin(user)) {
            long count = userRepository.countActiveSuperAdmins();
            if (count <= 1) {
                throw new BusinessException("不能禁用最后一个超级管理员");
            }
        }
    }

    public static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
