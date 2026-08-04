package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.PageResult;
import com.smartadmin.dto.RoleDetailVO;
import com.smartadmin.dto.RoleRequest;
import com.smartadmin.dto.RoleVO;
import com.smartadmin.entity.DataScope;
import com.smartadmin.entity.Permission;
import com.smartadmin.entity.Role;
import com.smartadmin.repository.PermissionRepository;
import com.smartadmin.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RbacService rbacService;
    private final AppCacheService appCacheService;

    public PageResult<RoleVO> list(int page, int size, String keyword) {
        rbacService.checkPermission("role:view");
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        Page<Role> result = roleRepository.search(StringUtils.hasText(keyword) ? keyword : "", pageable);
        List<RoleVO> records = result.getContent().stream().map(RoleVO::from).toList();
        return new PageResult<>(records, result.getTotalElements(), page, size);
    }

    public RoleDetailVO getById(Long id) {
        rbacService.checkPermission("role:view");
        Role role = roleRepository.findByIdWithPermissions(id)
                .orElseThrow(() -> new BusinessException("角色不存在"));
        List<Long> permissionIds = role.getPermissions().stream().map(Permission::getId).toList();
        return RoleDetailVO.from(role, permissionIds);
    }

    @Transactional
    public RoleVO create(RoleRequest request) {
        rbacService.checkPermission("role:create");
        if (roleRepository.existsByCode(request.getCode())) {
            throw new BusinessException("角色编码已存在");
        }
        Role role = new Role();
        role.setCode(request.getCode());
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        role.setBuiltIn(false);
        role.setDataScope(parseDataScope(request.getDataScope()));
        return RoleVO.from(roleRepository.save(role));
    }

    @Transactional
    public RoleVO update(Long id, RoleRequest request) {
        rbacService.checkPermission("role:update");
        Role role = findRole(id);
        if (Boolean.TRUE.equals(role.getBuiltIn())) {
            role.setDescription(request.getDescription());
            // 内置角色允许改数据范围（超级管理员仍强制 ALL）
            if (!RbacService.SUPER_ADMIN_CODE.equals(role.getCode())) {
                role.setDataScope(parseDataScope(request.getDataScope()));
            } else {
                role.setDataScope(DataScope.ALL);
            }
        } else {
            if (!role.getCode().equals(request.getCode()) && roleRepository.existsByCode(request.getCode())) {
                throw new BusinessException("角色编码已存在");
            }
            role.setCode(request.getCode());
            role.setName(request.getName());
            role.setDescription(request.getDescription());
            if (request.getStatus() != null) {
                role.setStatus(request.getStatus());
            }
            role.setDataScope(parseDataScope(request.getDataScope()));
        }
        return RoleVO.from(roleRepository.save(role));
    }

    @Transactional
    public void delete(Long id) {
        rbacService.checkPermission("role:delete");
        deleteInternal(id);
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        rbacService.checkPermission("role:delete");
        int count = 0;
        for (Long id : ids) {
            deleteInternal(id);
            count++;
        }
        return count;
    }

    private void deleteInternal(Long id) {
        Role role = findRole(id);
        if (Boolean.TRUE.equals(role.getBuiltIn())) {
            throw new BusinessException("内置角色不可删除：" + role.getName());
        }
        if (!role.getUsers().isEmpty()) {
            throw new BusinessException("角色已分配给用户，请先解绑：" + role.getName());
        }
        roleRepository.delete(role);
    }

    @Transactional
    public void updateStatus(Long id, Integer status) {
        rbacService.checkPermission("role:update");
        Role role = findRole(id);
        if (Boolean.TRUE.equals(role.getBuiltIn()) && RbacService.SUPER_ADMIN_CODE.equals(role.getCode())) {
            throw new BusinessException("不能禁用超级管理员角色");
        }
        role.setStatus(status);
        roleRepository.save(role);
    }

    @Transactional
    public void assignPermissions(Long id, List<Long> permissionIds) {
        rbacService.checkPermission("role:assign");
        Role role = findRole(id);
        if (Boolean.TRUE.equals(role.getBuiltIn()) && RbacService.SUPER_ADMIN_CODE.equals(role.getCode())) {
            throw new BusinessException("不能修改超级管理员角色的权限");
        }
        Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(permissionIds));
        role.setPermissions(permissions);
        roleRepository.save(role);
        appCacheService.evictAllPermissionCaches();
    }

    private Role findRole(Long id) {
        return roleRepository.findByIdWithPermissions(id)
                .orElse(roleRepository.findById(id)
                        .orElseThrow(() -> new BusinessException("角色不存在")));
    }

    public List<RoleVO> listOptions() {
        if (!rbacService.hasPermission("role:view") && !rbacService.hasPermission("user:view")) {
            throw new BusinessException(403, "无权限");
        }
        return roleRepository.findByStatusOrderByIdAsc(1).stream().map(RoleVO::from).toList();
    }

    private DataScope parseDataScope(String raw) {
        if (!StringUtils.hasText(raw)) {
            return DataScope.UNIT_AND_CHILDREN;
        }
        try {
            return DataScope.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("数据权限范围无效");
        }
    }
}
