package com.smartadmin.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.smartadmin.common.BusinessException;
import com.smartadmin.entity.Permission;
import com.smartadmin.entity.Role;
import com.smartadmin.entity.User;
import com.smartadmin.repository.PermissionRepository;
import com.smartadmin.repository.RoleRepository;
import com.smartadmin.repository.SysUnitRepository;
import com.smartadmin.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** RBAC 关键路径：超级管理员放行；普通用户按权限码拦截。 */
@ExtendWith(MockitoExtension.class)
class RbacServicePermissionTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private SysUnitRepository unitRepository;
    @Mock private AppCacheService appCacheService;

    private RbacService rbacService;

    @BeforeEach
    void setUp() {
        rbacService =
                new RbacService(
                        userRepository,
                        roleRepository,
                        permissionRepository,
                        unitRepository,
                        appCacheService);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void superAdminBypassesPermissionCheck() {
        User user = user("SuperAdmin", Set.of(role("SUPER_ADMIN")));
        authenticate("SuperAdmin");
        when(userRepository.findByUsernameWithRoles("SuperAdmin")).thenReturn(Optional.of(user));
        when(permissionRepository.findRoleCodesByUserId(user.getId()))
                .thenReturn(Set.of("SUPER_ADMIN"));

        assertDoesNotThrow(() -> rbacService.checkPermission("api:GET:/api/users"));
    }

    @Test
    void normalUserDeniedWithoutPermission() {
        User user = user("alice", Set.of(role("USER")));
        authenticate("alice");
        when(userRepository.findByUsernameWithRoles("alice")).thenReturn(Optional.of(user));
        when(permissionRepository.findRoleCodesByUserId(user.getId())).thenReturn(Set.of("USER"));
        when(appCacheService.getPermissionCodes(anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of("menu:dashboard"));

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> rbacService.checkPermission("api:DELETE:/api/users/{id}"));
        assertEquals(403, ex.getCode());
    }

    @Test
    void normalUserAllowedWithPermission() {
        User user = user("bob", Set.of(role("ADMIN")));
        authenticate("bob");
        when(userRepository.findByUsernameWithRoles("bob")).thenReturn(Optional.of(user));
        when(permissionRepository.findRoleCodesByUserId(user.getId())).thenReturn(Set.of("ADMIN"));
        when(appCacheService.getPermissionCodes(anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of("api:GET:/api/users"));

        assertDoesNotThrow(() -> rbacService.checkPermission("api:GET:/api/users"));
    }

    private static void authenticate(String username) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(username, "n/a", List.of()));
    }

    private static User user(String username, Set<Role> roles) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setStatus(1);
        user.setRoles(new HashSet<>(roles));
        return user;
    }

    private static Role role(String code) {
        Role role = new Role();
        role.setId(code.hashCode() & 0xffffL);
        role.setCode(code);
        role.setName(code);
        role.setPermissions(new HashSet<Permission>());
        return role;
    }
}
