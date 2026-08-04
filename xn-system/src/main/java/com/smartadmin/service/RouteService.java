package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.RouteRequest;
import com.smartadmin.dto.RouteVO;
import com.smartadmin.entity.Permission;
import com.smartadmin.entity.PermissionType;
import com.smartadmin.entity.Role;
import com.smartadmin.entity.RouteType;
import com.smartadmin.entity.SysRoute;
import com.smartadmin.entity.User;
import com.smartadmin.repository.PermissionRepository;
import com.smartadmin.repository.RoleRepository;
import com.smartadmin.repository.SysRouteRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final SysRouteRepository routeRepository;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final RbacService rbacService;
    private final AppCacheService appCacheService;

    public List<RouteVO> tree() {
        return tree(null, null, null, null);
    }

    public List<RouteVO> tree(String keyword, String type, Integer status, Boolean builtIn) {
        rbacService.checkPermission("route:view");
        List<RouteVO> tree = buildTree(routeRepository.findAllWithParent());
        if (!hasFilter(keyword, type, status, builtIn)) {
            return tree;
        }
        return filterTree(tree, keyword, type, status, builtIn);
    }

    private boolean hasFilter(String keyword, String type, Integer status, Boolean builtIn) {
        return StringUtils.hasText(keyword)
                || StringUtils.hasText(type)
                || status != null
                || builtIn != null;
    }

    private List<RouteVO> filterTree(
            List<RouteVO> nodes, String keyword, String type, Integer status, Boolean builtIn) {
        List<RouteVO> result = new ArrayList<>();
        for (RouteVO node : nodes) {
            List<RouteVO> children =
                    node.getChildren() == null || node.getChildren().isEmpty()
                            ? List.of()
                            : filterTree(node.getChildren(), keyword, type, status, builtIn);
            if (matchRoute(node, keyword, type, status, builtIn) || !children.isEmpty()) {
                node.setChildren(new ArrayList<>(children));
                result.add(node);
            }
        }
        return result;
    }

    private boolean matchRoute(
            RouteVO node, String keyword, String type, Integer status, Boolean builtIn) {
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim().toLowerCase();
            String haystack =
                    String.join(
                                    " ",
                                    nullToEmpty(node.getTitle()),
                                    nullToEmpty(node.getPath()),
                                    nullToEmpty(node.getPermission()),
                                    nullToEmpty(node.getViewPath()))
                            .toLowerCase();
            if (!haystack.contains(kw)) {
                return false;
            }
        }
        if (StringUtils.hasText(type)
                && (node.getType() == null || !type.equalsIgnoreCase(node.getType().name()))) {
            return false;
        }
        if (status != null && !status.equals(node.getStatus())) {
            return false;
        }
        if (builtIn != null && !builtIn.equals(Boolean.TRUE.equals(node.getBuiltIn()))) {
            return false;
        }
        return true;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 隐藏路由（如字典数据下钻页）仍需下发给前端以便动态注册 Vue Router 路由， 只是不出现在侧边栏导航中（前端 filterHiddenMenus 负责侧边栏过滤）。 */
    public List<RouteVO> menuTreeForCurrentUser() {
        User user = rbacService.currentUser();
        return appCacheService.getMenus(
                user.getId(),
                new tools.jackson.core.type.TypeReference<>() {},
                () -> {
                    List<RouteVO> tree =
                            buildTree(
                                    routeRepository.findAllWithParent().stream()
                                            .filter(r -> r.getStatus() == 1)
                                            .toList());
                    return filterByPermission(tree);
                });
    }

    public RouteVO getById(Long id) {
        rbacService.checkPermission("route:view");
        SysRoute route = findRoute(id);
        return RouteVO.from(route);
    }

    @Transactional
    public RouteVO create(RouteRequest request) {
        rbacService.checkPermission("route:create");
        validateRequest(request, null);
        SysRoute route = new SysRoute();
        applyRequest(route, request);
        route.setBuiltIn(false);
        route = routeRepository.save(route);
        ensurePermissionCode(route, null, true);
        route = routeRepository.save(route);
        syncMenuPermission(route, true);
        appCacheService.evictByPrefix(AppCacheService.PREFIX_MENUS);
        appCacheService.evictSiteUiShots();
        return RouteVO.from(route);
    }

    @Transactional
    public RouteVO update(Long id, RouteRequest request) {
        rbacService.checkPermission("route:update");
        SysRoute route = findRoute(id);
        validateRequest(request, id);
        if (Boolean.TRUE.equals(route.getBuiltIn()) && request.getType() != route.getType()) {
            throw new BusinessException("内置路由不可修改类型");
        }
        String oldPath = route.getPath();
        applyRequest(route, request);
        ensurePermissionCode(route, oldPath, false);
        route = routeRepository.save(route);
        syncMenuPermission(route, false);
        appCacheService.evictByPrefix(AppCacheService.PREFIX_MENUS);
        appCacheService.evictSiteUiShots();
        return RouteVO.from(route);
    }

    @Transactional
    public void delete(Long id) {
        rbacService.checkPermission("route:delete");
        deleteInternal(id);
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        rbacService.checkPermission("route:delete");
        int count = 0;
        for (Long id : ids) {
            deleteInternal(id);
            count++;
        }
        return count;
    }

    private void deleteInternal(Long id) {
        SysRoute route = findRoute(id);
        if (Boolean.TRUE.equals(route.getBuiltIn())) {
            throw new BusinessException("内置路由不可删除：" + route.getTitle());
        }
        if (routeRepository.countByParentId(id) > 0) {
            throw new BusinessException("存在子路由，无法删除：" + route.getTitle());
        }
        routeRepository.delete(route);
        appCacheService.evictByPrefix(AppCacheService.PREFIX_MENUS);
        appCacheService.evictAllPermissionCaches();
        appCacheService.evictSiteUiShots();
    }

    public static String pathToViewPath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    /** /system/roles → menu:system:roles */
    public static String pathToPermissionCode(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String cleaned = path.startsWith("/") ? path.substring(1) : path;
        cleaned = cleaned.replaceAll("/+", "/").replaceAll("^/|/$", "");
        if (!StringUtils.hasText(cleaned)) {
            return "menu";
        }
        String code = "menu:" + cleaned.replace('/', ':').toLowerCase(Locale.ROOT);
        return code.replaceAll("[^a-z0-9:_-]", "-");
    }

    private void validateRequest(RouteRequest request, Long excludeId) {
        if (request.getType() == RouteType.MENU) {
            if (!StringUtils.hasText(request.getPath())) {
                throw new BusinessException("菜单路由必须填写访问路径");
            }
            request.setPath(normalizePath(request.getPath()));
            routeRepository
                    .findByPath(request.getPath())
                    .ifPresent(
                            existing -> {
                                if (excludeId == null || !existing.getId().equals(excludeId)) {
                                    throw new BusinessException("访问路径已存在: " + request.getPath());
                                }
                            });
        }
    }

    private void applyRequest(SysRoute route, RouteRequest request) {
        route.setTitle(request.getTitle());
        if (request.getType() == RouteType.MENU) {
            String path = normalizePath(request.getPath());
            route.setPath(path);
            route.setViewPath(pathToViewPath(path));
        } else {
            route.setPath(null);
            route.setViewPath(null);
        }
        route.setIcon(request.getIcon());
        // permission 由系统自动生成，忽略前端传入
        route.setType(request.getType());
        route.setSort(request.getSort() != null ? request.getSort() : 0);
        route.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        route.setHidden(request.getHidden() != null ? request.getHidden() : false);
        route.setAffix(request.getAffix() != null ? request.getAffix() : false);
        route.setPermissionControl(
                request.getPermissionControl() != null ? request.getPermissionControl() : false);
        if (request.getParentId() != null) {
            route.setParent(findRoute(request.getParentId()));
        } else {
            route.setParent(null);
        }
    }

    /** 补全前导 /，统一路径格式 */
    private static String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return path;
        }
        String cleaned = path.trim().replace('\\', '/').replaceAll("/+", "/");
        if (cleaned.endsWith("/") && cleaned.length() > 1) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned.startsWith("/") ? cleaned : "/" + cleaned;
    }

    /** 菜单：按访问路径生成；目录：按父级权限 + 自身 id 生成。 已有标识在路径未变时保留，避免破坏已分配的内置权限。 */
    private void ensurePermissionCode(SysRoute route, String oldPath, boolean isNew) {
        if (route.getType() == RouteType.MENU) {
            String generated = pathToPermissionCode(route.getPath());
            boolean pathChanged = isNew || oldPath == null || !oldPath.equals(route.getPath());
            String expectedOld = pathToPermissionCode(oldPath);
            if (!StringUtils.hasText(route.getPermission())) {
                route.setPermission(generated);
            } else if (pathChanged
                    && expectedOld != null
                    && expectedOld.equals(route.getPermission())) {
                // 原先就是按路径自动生成的，路径变更时同步更新
                route.setPermission(generated);
            }
            return;
        }
        if (!StringUtils.hasText(route.getPermission())) {
            String parentCode =
                    route.getParent() != null
                                    && StringUtils.hasText(route.getParent().getPermission())
                            ? route.getParent().getPermission()
                            : "menu";
            route.setPermission(parentCode + ":g" + route.getId());
        }
    }

    /** 同步到权限内容（MENU 类型），新建时自动授予管理员角色 */
    private void syncMenuPermission(SysRoute route, boolean grantAdmins) {
        if (!StringUtils.hasText(route.getPermission())) {
            return;
        }
        Permission permission = permissionRepository.findByCode(route.getPermission()).orElse(null);
        if (permission == null) {
            permission = new Permission();
            permission.setCode(route.getPermission());
            permission.setType(PermissionType.MENU);
            permission.setBuiltIn(false);
            permission.setSort(route.getSort() != null ? route.getSort() : 0);
        }
        permission.setName(route.getTitle());
        permission.setPath(route.getPath());
        if (route.getParent() != null && StringUtils.hasText(route.getParent().getPermission())) {
            permissionRepository
                    .findByCode(route.getParent().getPermission())
                    .ifPresent(permission::setParent);
        } else {
            permission.setParent(null);
        }
        permission = permissionRepository.save(permission);

        if (grantAdmins) {
            grantPermissionToAdminRoles(permission);
        }
    }

    private void grantPermissionToAdminRoles(Permission permission) {
        for (String code : List.of("SUPER_ADMIN", "ADMIN")) {
            roleRepository
                    .findByCode(code)
                    .ifPresent(
                            role -> {
                                Role managed =
                                        roleRepository
                                                .findByIdWithPermissions(role.getId())
                                                .orElse(role);
                                Set<Permission> perms = new HashSet<>(managed.getPermissions());
                                if (perms.add(permission)) {
                                    managed.setPermissions(perms);
                                    roleRepository.save(managed);
                                }
                            });
        }
    }

    private SysRoute findRoute(Long id) {
        return routeRepository.findById(id).orElseThrow(() -> new BusinessException("路由不存在"));
    }

    private List<RouteVO> buildTree(List<SysRoute> routes) {
        List<RouteVO> roots = new ArrayList<>();
        for (SysRoute route : routes) {
            if (route.getParent() == null) {
                roots.add(buildNode(route, routes));
            }
        }
        roots.sort(Comparator.comparing(RouteVO::getSort));
        return roots;
    }

    private RouteVO buildNode(SysRoute parent, List<SysRoute> all) {
        RouteVO vo = RouteVO.from(parent);
        List<RouteVO> children =
                all.stream()
                        .filter(
                                r ->
                                        r.getParent() != null
                                                && r.getParent().getId().equals(parent.getId()))
                        .sorted(Comparator.comparing(SysRoute::getSort))
                        .map(r -> buildNode(r, all))
                        .toList();
        vo.setChildren(new ArrayList<>(children));
        return vo;
    }

    private List<RouteVO> filterByPermission(List<RouteVO> nodes) {
        List<RouteVO> result = new ArrayList<>();
        for (RouteVO node : nodes) {
            List<RouteVO> filteredChildren = filterByPermission(node.getChildren());
            // 未开启权限控制的菜单对所有登录用户可见
            boolean needsControl = Boolean.TRUE.equals(node.getPermissionControl());
            boolean hasPermission =
                    !needsControl
                            || !StringUtils.hasText(node.getPermission())
                            || rbacService.hasPermission(node.getPermission());
            if (node.getType() == RouteType.DIR) {
                if (!filteredChildren.isEmpty()) {
                    node.setChildren(filteredChildren);
                    result.add(node);
                }
            } else if (hasPermission) {
                node.setChildren(filteredChildren);
                result.add(node);
            }
        }
        return result;
    }
}
