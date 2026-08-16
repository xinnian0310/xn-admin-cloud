package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.MenuPermissionGroupVO;
import com.smartadmin.dto.PermissionRequest;
import com.smartadmin.dto.PermissionVO;
import com.smartadmin.entity.Permission;
import com.smartadmin.entity.PermissionType;
import com.smartadmin.repository.PermissionRepository;
import com.smartadmin.security.ApiPermissionRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final RbacService rbacService;
    private final ApiPermissionRegistry apiPermissionRegistry;

    public List<PermissionVO> tree() {
        if (!rbacService.hasPermission("permission:view")
                && !rbacService.hasPermission("role:assign")) {
            throw new BusinessException(403, "无权限");
        }
        return permissionRepository.findByParentIsNullOrderBySortAsc().stream()
                .map(PermissionVO::treeFrom)
                .toList();
    }

    public MenuPermissionGroupVO groupedByMenu(Long menuId) {
        rbacService.checkPermission("permission:assign");
        Permission menu = findPermission(menuId);
        if (menu.getType() != PermissionType.MENU) {
            throw new BusinessException("仅菜单类型权限支持分配子权限");
        }
        MenuPermissionGroupVO vo = new MenuPermissionGroupVO();
        vo.setMenuId(menu.getId());
        vo.setMenuName(menu.getName());
        vo.setMenuCode(menu.getCode());
        for (Permission child : permissionRepository.findByParentIdOrderBySortAsc(menuId)) {
            PermissionVO item = PermissionVO.from(child);
            switch (child.getType()) {
                case API -> vo.getApi().add(item);
                case BUTTON -> vo.getButton().add(item);
                case TABLE_BUTTON -> vo.getTableButton().add(item);
                default -> {}
            }
        }
        return vo;
    }

    @Transactional
    public PermissionVO create(PermissionRequest request) {
        rbacService.checkPermission("permission:create");
        if (permissionRepository.existsByCode(request.getCode())) {
            throw new BusinessException("权限编码已存在");
        }
        Permission permission = new Permission();
        applyRequest(permission, request);
        permission.setBuiltIn(false);
        PermissionVO vo = PermissionVO.from(permissionRepository.save(permission));
        apiPermissionRegistry.reload();
        return vo;
    }

    @Transactional
    public PermissionVO update(Long id, PermissionRequest request) {
        rbacService.checkPermission("permission:update");
        Permission permission = findPermission(id);
        permission.setName(request.getName());
        permission.setSort(request.getSort() != null ? request.getSort() : permission.getSort());
        if (!Boolean.TRUE.equals(permission.getBuiltIn())) {
            permission.setPath(request.getPath());
            permission.setMethod(request.getMethod());
        }
        // action/icon/buttonColor 属于 UI 元数据，内置权限也允许调整
        permission.setAction(request.getAction());
        permission.setIcon(request.getIcon());
        permission.setIconAntd(request.getIconAntd());
        permission.setButtonColor(request.getButtonColor());
        PermissionVO vo = PermissionVO.from(permissionRepository.save(permission));
        apiPermissionRegistry.reload();
        return vo;
    }

    @Transactional
    public void delete(Long id) {
        rbacService.checkPermission("permission:delete");
        Permission permission = findPermission(id);
        if (Boolean.TRUE.equals(permission.getBuiltIn())) {
            throw new BusinessException("内置权限不可删除");
        }
        if (permissionRepository.countByParentId(id) > 0) {
            throw new BusinessException("存在子权限，无法删除");
        }
        if (!permission.getRoles().isEmpty()) {
            throw new BusinessException("权限已分配给角色，无法删除");
        }
        permissionRepository.delete(permission);
        apiPermissionRegistry.reload();
    }

    private void applyRequest(Permission permission, PermissionRequest request) {
        permission.setCode(request.getCode());
        permission.setName(request.getName());
        permission.setType(request.getType());
        permission.setPath(request.getPath());
        permission.setMethod(request.getMethod());
        permission.setAction(request.getAction());
        permission.setIcon(request.getIcon());
        permission.setIconAntd(request.getIconAntd());
        permission.setButtonColor(request.getButtonColor());
        permission.setSort(request.getSort() != null ? request.getSort() : 0);
        if (request.getParentId() != null) {
            Permission parent = findPermission(request.getParentId());
            permission.setParent(parent);
        }
    }

    private Permission findPermission(Long id) {
        return permissionRepository.findById(id).orElseThrow(() -> new BusinessException("权限不存在"));
    }
}
