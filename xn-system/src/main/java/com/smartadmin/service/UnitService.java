package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.UnitRequest;
import com.smartadmin.dto.UnitVO;
import com.smartadmin.entity.Role;
import com.smartadmin.entity.SysUnit;
import com.smartadmin.repository.SysUnitRepository;
import com.smartadmin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UnitService {

    private final SysUnitRepository unitRepository;
    private final UserRepository userRepository;
    private final RbacService rbacService;
    private final DataScopeService dataScopeService;
    private final AppCacheService appCacheService;

    public List<UnitVO> tree(String keyword, Integer status) {
        rbacService.checkPermission("unit:view");
        List<SysUnit> all = unitRepository.findAllWithRoles().stream()
                .sorted(Comparator
                        .comparing((SysUnit u) -> u.getSort() == null ? 0 : u.getSort())
                        .thenComparing(SysUnit::getId))
                .toList();
        all = dataScopeService.filterUnits(all);
        List<UnitVO> flat = all.stream()
                .filter(u -> status == null || Objects.equals(u.getStatus(), status))
                .map(UnitVO::from)
                .toList();
        List<UnitVO> tree = buildTree(flat);
        if (!StringUtils.hasText(keyword)) {
            return tree;
        }
        String kw = keyword.trim().toLowerCase();
        return filterTree(tree, kw);
    }

    public List<UnitVO> listOptions() {
        rbacService.checkPermission("unit:view");
        List<SysUnit> all = dataScopeService.filterUnits(unitRepository.findByStatusWithRoles(1));
        return all.stream()
                .sorted(Comparator
                        .comparing((SysUnit u) -> u.getSort() == null ? 0 : u.getSort())
                        .thenComparing(SysUnit::getId))
                .map(UnitVO::from)
                .toList();
    }

    public UnitVO getById(Long id) {
        rbacService.checkPermission("unit:view");
        dataScopeService.assertUnitAccessible(id);
        return UnitVO.from(findUnitWithRoles(id));
    }

    /** 自身 + 全部子孙单位 id，用于用户列表按单位树筛选 */
    public List<Long> collectSelfAndDescendantIds(Long unitId) {
        return dataScopeService.collectSelfAndDescendantIds(unitId);
    }

    @Transactional
    public UnitVO create(UnitRequest request) {
        rbacService.checkPermission("unit:create");
        if (unitRepository.existsByCode(request.getCode())) {
            throw new BusinessException("单位编码已存在");
        }
        validateParent(request.getParentId(), null);
        SysUnit unit = new SysUnit();
        applyRequest(unit, request);
        unit.setBuiltIn(false);
        applyRoles(unit, request.getRoleIds());
        UnitVO vo = UnitVO.from(unitRepository.save(unit));
        appCacheService.evictAllPermissionCaches();
        return vo;
    }

    @Transactional
    public UnitVO update(Long id, UnitRequest request) {
        rbacService.checkPermission("unit:update");
        dataScopeService.assertUnitAccessible(id);
        SysUnit unit = findUnitWithRoles(id);
        if (Boolean.TRUE.equals(unit.getBuiltIn())) {
            unit.setDescription(request.getDescription());
            if (request.getSort() != null) {
                unit.setSort(request.getSort());
            }
        } else {
            if (!unit.getCode().equals(request.getCode()) && unitRepository.existsByCode(request.getCode())) {
                throw new BusinessException("单位编码已存在");
            }
            validateParent(request.getParentId(), id);
            applyRequest(unit, request);
        }
        applyRoles(unit, request.getRoleIds());
        UnitVO vo = UnitVO.from(unitRepository.save(unit));
        appCacheService.evictAllPermissionCaches();
        return vo;
    }

    @Transactional
    public void assignRoles(Long id, List<Long> roleIds) {
        rbacService.checkPermission("unit:assign");
        dataScopeService.assertUnitAccessible(id);
        SysUnit unit = findUnitWithRoles(id);
        applyRoles(unit, roleIds);
        unitRepository.save(unit);
        appCacheService.evictAllPermissionCaches();
    }

    @Transactional
    public void delete(Long id) {
        rbacService.checkPermission("unit:delete");
        deleteInternal(id);
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        rbacService.checkPermission("unit:delete");
        int count = 0;
        for (Long id : ids) {
            deleteInternal(id);
            count++;
        }
        return count;
    }

    @Transactional
    public void updateStatus(Long id, Integer status) {
        rbacService.checkPermission("unit:update");
        dataScopeService.assertUnitAccessible(id);
        SysUnit unit = findUnit(id);
        if (Boolean.TRUE.equals(unit.getBuiltIn()) && status != null && status == 0) {
            throw new BusinessException("内置单位不可禁用");
        }
        unit.setStatus(status);
        unitRepository.save(unit);
    }

    private void deleteInternal(Long id) {
        dataScopeService.assertUnitAccessible(id);
        SysUnit unit = findUnitWithRoles(id);
        if (Boolean.TRUE.equals(unit.getBuiltIn())) {
            throw new BusinessException("内置单位不可删除：" + unit.getName());
        }
        if (unitRepository.countByParentId(id) > 0) {
            throw new BusinessException("存在子单位，无法删除：" + unit.getName());
        }
        if (userRepository.countByUnit_Id(id) > 0) {
            throw new BusinessException("单位下仍有用户，请先调整用户单位：" + unit.getName());
        }
        unit.getRoles().clear();
        unitRepository.delete(unit);
    }

    private void applyRequest(SysUnit unit, UnitRequest request) {
        unit.setCode(request.getCode().trim());
        unit.setName(request.getName().trim());
        unit.setParentId(request.getParentId());
        unit.setDescription(request.getDescription());
        unit.setSort(request.getSort() != null ? request.getSort() : 0);
        unit.setStatus(request.getStatus() != null ? request.getStatus() : 1);
    }

    private void applyRoles(SysUnit unit, List<Long> roleIds) {
        if (roleIds == null) {
            unit.setRoles(new HashSet<>());
            return;
        }
        if (roleIds.isEmpty()) {
            unit.setRoles(new HashSet<>());
            return;
        }
        rbacService.validateRoleAssignment(roleIds);
        Set<Role> roles = rbacService.loadRolesByIds(roleIds);
        unit.setRoles(roles);
    }

    private void validateParent(Long parentId, Long selfId) {
        if (parentId == null) {
            return;
        }
        if (selfId != null && parentId.equals(selfId)) {
            throw new BusinessException("上级单位不能是自己");
        }
        SysUnit parent = unitRepository.findById(parentId)
                .orElseThrow(() -> new BusinessException("上级单位不存在"));
        if (selfId != null) {
            Set<Long> descendants = new HashSet<>(collectSelfAndDescendantIds(selfId));
            if (descendants.contains(parentId)) {
                throw new BusinessException("上级单位不能是当前单位的子级");
            }
        }
        if (parent.getStatus() != null && parent.getStatus() == 0) {
            throw new BusinessException("上级单位已禁用");
        }
    }

    private SysUnit findUnit(Long id) {
        return unitRepository.findById(id)
                .orElseThrow(() -> new BusinessException("单位不存在"));
    }

    private SysUnit findUnitWithRoles(Long id) {
        return unitRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new BusinessException("单位不存在"));
    }

    private List<UnitVO> buildTree(List<UnitVO> flat) {
        Map<Long, UnitVO> map = new HashMap<>();
        for (UnitVO vo : flat) {
            map.put(vo.getId(), vo);
        }
        List<UnitVO> roots = new ArrayList<>();
        for (UnitVO vo : flat) {
            if (vo.getParentId() != null && map.containsKey(vo.getParentId())) {
                map.get(vo.getParentId()).getChildren().add(vo);
            } else {
                roots.add(vo);
            }
        }
        return roots;
    }

    private List<UnitVO> filterTree(List<UnitVO> nodes, String keyword) {
        List<UnitVO> result = new ArrayList<>();
        for (UnitVO node : nodes) {
            List<UnitVO> children = filterTree(node.getChildren(), keyword);
            boolean matched = (node.getName() != null && node.getName().toLowerCase().contains(keyword))
                    || (node.getCode() != null && node.getCode().toLowerCase().contains(keyword));
            if (matched || !children.isEmpty()) {
                UnitVO copy = copyNode(node);
                copy.setChildren(children);
                result.add(copy);
            }
        }
        return result;
    }

    private UnitVO copyNode(UnitVO src) {
        UnitVO vo = new UnitVO();
        vo.setId(src.getId());
        vo.setCode(src.getCode());
        vo.setName(src.getName());
        vo.setParentId(src.getParentId());
        vo.setDescription(src.getDescription());
        vo.setSort(src.getSort());
        vo.setStatus(src.getStatus());
        vo.setBuiltIn(src.getBuiltIn());
        vo.setRoleIds(src.getRoleIds());
        vo.setRoleList(src.getRoleList());
        return vo;
    }
}
