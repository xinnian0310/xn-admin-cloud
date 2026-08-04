package com.smartadmin.config;

import com.smartadmin.entity.Permission;
import com.smartadmin.entity.PermissionType;
import com.smartadmin.entity.Role;
import com.smartadmin.entity.RouteType;
import com.smartadmin.entity.SysRoute;
import com.smartadmin.repository.PermissionRepository;
import com.smartadmin.repository.RoleRepository;
import com.smartadmin.repository.SysRouteRepository;
import com.smartadmin.service.AppCacheService;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@Order(2)
@RequiredArgsConstructor
public class RouteInitializer implements CommandLineRunner {

    /** 组织与账号 */
    public static final String PERM_ORG_GROUP = "menu:system:org";

    /** 权限与安全（历史码 menu:system:rbac，保留兼容） */
    public static final String PERM_RBAC_GROUP = "menu:system:rbac";

    /** 内容运营 */
    public static final String PERM_CONTENT_GROUP = "menu:system:content";

    /** 基础数据（字典等） */
    public static final String PERM_BASE_GROUP = "menu:system:base";

    /** 系统设置（登录页、系统配置） */
    public static final String PERM_SETTINGS_GROUP = "menu:system:settings";

    /** 系统工具（文件、定时任务、接口文档） */
    public static final String PERM_TOOLS_GROUP = "menu:system:tools";

    /** 日志管理（登录日志、操作日志），挂在系统监控下 */
    public static final String PERM_LOGS_GROUP = "menu:monitor:logs";

    /** 个人中心（顶级目录） */
    public static final String PERM_PERSONAL_GROUP = "menu:personal";

    private final SysRouteRepository routeRepository;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final AppCacheService appCacheService;

    @Override
    @Transactional
    public void run(String... args) {
        if (routeRepository.count() == 0) {
            initRoutes();
        }
        ensurePermissionRouteRenamed();
        ensurePermissionContentRoute();
        ensureUnitRoute();
        ensureNoticeRoute();
        ensureMessageRoute();
        ensureDictRoute();
        ensureDictDataRoute();
        ensureLoginPageRoute();
        ensureSystemConfigRoute();
        ensureSiteContactRoute();
        ensureSecurityPolicyRoute();
        ensureLoginLogRoute();
        ensureOperLogRoute();
        ensureExceptionLogRoute();
        ensureProfileRoute();
        ensureMineMessageRoute();
        ensureFileRoute();
        ensureJobRoute();
        ensureJobLogRoute();
        ensureApiDocsRoute();
        ensurePostRoute();
        ensureRecycleRoute();
        ensureCodegenRoute();
        ensureSystemMenuStructure();
        ensureHomeRoute();
        ensureMonitorRoutes();
        ensureRouteIcons();
        ensurePermissionControlDefaults();
        syncMenuPermissionsFromRoutes();
        // 补齐内置菜单后清菜单/权限缓存，避免 Redis 旧树导致侧栏看不到新菜单
        appCacheService.evictAllPermissionCaches();
    }

    /** 首次补齐「权限控制」开关：默认关闭； 用户管理 / 路由管理 / 角色列表 / 权限内容 / 单位 / 公告 开启。 */
    private void ensurePermissionControlDefaults() {
        List<SysRoute> all = routeRepository.findAll();
        boolean alreadyConfigured =
                all.stream().anyMatch(r -> Boolean.TRUE.equals(r.getPermissionControl()));
        if (alreadyConfigured) {
            return;
        }
        Set<String> controlledPaths =
                Set.of(
                        "/users",
                        "/system/routes",
                        "/system/roles",
                        "/system/units",
                        "/system/posts",
                        "/system/permissions-content",
                        "/system/notices",
                        "/system/dicts",
                        "/system/dicts/data",
                        "/system/login-settings",
                        "/system/config",
                        "/system/security",
                        "/system/logs/login",
                        "/system/logs/oper",
                        "/system/logs/exception",
                        "/system/messages",
                        "/system/files",
                        "/system/jobs",
                        "/system/jobs/logs",
                        "/system/api-docs",
                        "/system/recycle",
                        "/system/codegen",
                        "/messages/mine",
                        "/monitor/redis",
                        "/monitor/sql");
        for (SysRoute route : all) {
            if (route.getType() == RouteType.MENU
                    && route.getPath() != null
                    && controlledPaths.contains(route.getPath())) {
                route.setPermissionControl(true);
                routeRepository.save(route);
            }
        }
    }

    /**
     * 系统管理下按业务分组，个人信息移出到顶级「个人中心」。
     *
     * <pre>
     * 首页
     * 个人中心 → 个人信息 / 我的消息
     * 系统监控 → 在线用户 / 服务监控 / 缓存监控 / SQL监控 / 日志管理
     * 系统管理
     * ├── 组织与账号（用户、单位、岗位）
     * ├── 权限与安全（角色列表、角色权限、权限内容、路由）
     * ├── 内容运营（公告、站内信）
     * ├── 基础数据（字典）
     * ├── 系统设置（登录页、系统配置）
     * └── 系统工具（文件、定时任务、任务日志、接口文档）
     * </pre>
     */
    private void ensureSystemMenuStructure() {
        SysRoute system = findDirByPermission("menu:system");
        if (system == null) {
            return;
        }

        SysRoute orgGroup = ensureDir(PERM_ORG_GROUP, "组织与账号", "OfficeBuilding", system, 1);
        SysRoute rbacGroup = ensureRbacGroup(system, 2);
        SysRoute contentGroup = ensureDir(PERM_CONTENT_GROUP, "内容运营", "Notebook", system, 3);
        SysRoute baseGroup = ensureDir(PERM_BASE_GROUP, "基础数据", "Collection", system, 4);
        SysRoute settingsGroup = ensureDir(PERM_SETTINGS_GROUP, "系统设置", "Tools", system, 5);
        SysRoute toolsGroup = ensureDir(PERM_TOOLS_GROUP, "系统工具", "Suitcase", system, 6);
        SysRoute personalGroup = ensureDir(PERM_PERSONAL_GROUP, "个人中心", "UserFilled", null, 2);

        setParentAndSort(orgGroup, system, 1);
        setParentAndSort(rbacGroup, system, 2);
        setParentAndSort(contentGroup, system, 3);
        setParentAndSort(baseGroup, system, 4);
        setParentAndSort(settingsGroup, system, 5);
        setParentAndSort(toolsGroup, system, 6);

        // 组织与账号
        moveMenu("/users", "menu:system:user", orgGroup, 1);
        moveMenu("/system/units", "menu:system:unit", orgGroup, 2);
        moveMenu("/system/posts", "menu:system:post", orgGroup, 3);

        // 权限与安全
        moveMenu("/system/roles", "menu:system:role", rbacGroup, 1);
        moveMenu("/system/permissions", "menu:system:permission", rbacGroup, 2);
        moveMenu("/system/permissions-content", "menu:system:permission-content", rbacGroup, 3);
        moveMenu("/system/routes", "menu:system:route", rbacGroup, 4);

        // 内容运营
        moveMenu("/system/notices", "menu:system:notice", contentGroup, 1);
        moveMenu("/system/messages", "menu:system:message", contentGroup, 2);

        // 基础数据
        moveMenu("/system/dicts", "menu:system:dict", baseGroup, 1);
        // 字典数据为隐藏子路由，始终挂在字典管理菜单下
        routeRepository
                .findByPath("/system/dicts")
                .ifPresent(
                        dictRoute ->
                                moveMenu(
                                        "/system/dicts/data",
                                        "menu:system:dict-data",
                                        dictRoute,
                                        1));

        // 系统设置
        moveMenu("/system/login-settings", "menu:system:login-page", settingsGroup, 1);
        moveMenu("/system/config", "menu:system:config", settingsGroup, 2);
        moveMenu("/system/security", "menu:system:security", settingsGroup, 3);
        moveMenu("/system/site-contact", "menu:system:site-contact", settingsGroup, 4);
        routeRepository
                .findByPath("/system/site-contact")
                .ifPresent(
                        route -> {
                            if (Boolean.TRUE.equals(route.getPermissionControl())) {
                                route.setPermissionControl(false);
                                routeRepository.save(route);
                            }
                        });

        // 系统工具
        moveMenu("/system/files", "menu:system:file", toolsGroup, 1);
        moveMenu("/system/jobs", "menu:system:job", toolsGroup, 2);
        moveMenu("/system/jobs/logs", "menu:system:job-log", toolsGroup, 3);
        moveMenu("/system/api-docs", "menu:system:api-docs", toolsGroup, 4);
        moveMenu("/system/recycle", "menu:system:recycle", toolsGroup, 5);
        moveMenu("/system/codegen", "menu:system:codegen", toolsGroup, 6);

        // 个人中心（顶级，不在系统管理下）
        if (personalGroup.getParent() != null) {
            personalGroup.setParent(null);
            routeRepository.save(personalGroup);
        }
        if (personalGroup.getSort() == null || personalGroup.getSort() != 2) {
            personalGroup.setSort(2);
            routeRepository.save(personalGroup);
        }
        moveMenu("/profile", "menu:profile", personalGroup, 1);
        moveMenu("/messages/mine", "menu:personal:message", personalGroup, 2);
        routeRepository
                .findByPath("/profile")
                .ifPresent(
                        route -> {
                            if (Boolean.TRUE.equals(route.getPermissionControl())) {
                                route.setPermissionControl(false);
                                routeRepository.save(route);
                            }
                        });

        // 一级菜单排序：首页(1) → 个人中心(2) → 系统监控(3) → 系统管理(4)
        routeRepository
                .findByPath("/dashboard")
                .ifPresent(
                        route -> {
                            if (route.getSort() == null || route.getSort() != 1) {
                                route.setSort(1);
                                routeRepository.save(route);
                            }
                        });
        if (system.getSort() == null || system.getSort() != 4) {
            system.setSort(4);
            routeRepository.save(system);
        }
    }

    private SysRoute ensureRbacGroup(SysRoute system, int sort) {
        SysRoute group = findRbacGroupRoute();
        if (group == null) {
            return ensureDir(PERM_RBAC_GROUP, "权限与安全", "Lock", system, sort);
        }
        boolean dirty = false;
        if (!"权限与安全".equals(group.getTitle())) {
            group.setTitle("权限与安全");
            dirty = true;
        }
        if (!"Lock".equals(group.getIcon())) {
            group.setIcon("Lock");
            dirty = true;
        }
        if (!PERM_RBAC_GROUP.equals(group.getPermission())) {
            group.setPermission(PERM_RBAC_GROUP);
            dirty = true;
        }
        if (dirty) {
            routeRepository.save(group);
        }
        return group;
    }

    private SysRoute ensureDir(
            String permission, String title, String icon, SysRoute parent, int sort) {
        SysRoute existing = findDirByPermission(permission);
        if (existing != null) {
            boolean dirty = false;
            if (!title.equals(existing.getTitle())) {
                existing.setTitle(title);
                dirty = true;
            }
            if (!icon.equals(existing.getIcon())) {
                existing.setIcon(icon);
                dirty = true;
            }
            if (dirty) {
                routeRepository.save(existing);
            }
            return existing;
        }
        // 兼容：按旧标题找（权限管理 → 权限与安全）
        if (PERM_RBAC_GROUP.equals(permission)) {
            SysRoute legacy = findRbacGroupRoute();
            if (legacy != null) {
                return ensureRbacGroup(parent, sort);
            }
        }
        return saveDirStandalone(title, icon, permission, parent, sort);
    }

    private void moveMenu(String path, String permission, SysRoute parent, int sort) {
        routeRepository
                .findByPath(path)
                .ifPresent(
                        route -> {
                            boolean dirty = false;
                            if (permission != null && !permission.equals(route.getPermission())) {
                                route.setPermission(permission);
                                dirty = true;
                            }
                            if (parent != null
                                    && (route.getParent() == null
                                            || !Objects.equals(
                                                    route.getParent().getId(), parent.getId()))) {
                                route.setParent(parent);
                                dirty = true;
                            }
                            if (route.getSort() == null || route.getSort() != sort) {
                                route.setSort(sort);
                                dirty = true;
                            }
                            if (dirty) {
                                routeRepository.save(route);
                            }
                        });
    }

    private void setParentAndSort(SysRoute route, SysRoute parent, int sort) {
        boolean dirty = false;
        if (parent != null
                && (route.getParent() == null
                        || !Objects.equals(route.getParent().getId(), parent.getId()))) {
            route.setParent(parent);
            dirty = true;
        }
        if (route.getSort() == null || route.getSort() != sort) {
            route.setSort(sort);
            dirty = true;
        }
        if (dirty) {
            routeRepository.save(route);
        }
    }

    private SysRoute findDirByPermission(String permission) {
        return routeRepository.findAll().stream()
                .filter(r -> r.getType() == RouteType.DIR && permission.equals(r.getPermission()))
                .findFirst()
                .orElse(null);
    }

    private SysRoute findRbacGroupRoute() {
        SysRoute byCode = findDirByPermission(PERM_RBAC_GROUP);
        if (byCode != null) {
            return byCode;
        }
        return routeRepository.findAll().stream()
                .filter(
                        r ->
                                r.getType() == RouteType.DIR
                                        && "menu:system:role".equals(r.getPermission()))
                .findFirst()
                .orElseGet(
                        () ->
                                routeRepository.findAll().stream()
                                        .filter(
                                                r ->
                                                        r.getType() == RouteType.DIR
                                                                && ("权限管理".equals(r.getTitle())
                                                                        || "权限与安全"
                                                                                .equals(
                                                                                        r
                                                                                                .getTitle())
                                                                        || "角色权限"
                                                                                .equals(
                                                                                        r
                                                                                                .getTitle())))
                                        .findFirst()
                                        .orElse(null));
    }

    /** 个人信息：挂到个人中心（由 ensureSystemMenuStructure 最终定位） */
    private void ensureProfileRoute() {
        String path = "/profile";
        SysRoute existing = routeRepository.findByPath(path).orElse(null);
        if (existing != null) {
            boolean dirty = false;
            if (!"个人信息".equals(existing.getTitle())) {
                existing.setTitle("个人信息");
                dirty = true;
            }
            if (!"UserFilled".equals(existing.getIcon())) {
                existing.setIcon("UserFilled");
                dirty = true;
            }
            if (!"menu:profile".equals(existing.getPermission())) {
                existing.setPermission("menu:profile");
                dirty = true;
            }
            if (!"profile".equals(existing.getViewPath())) {
                existing.setViewPath("profile");
                dirty = true;
            }
            if (Boolean.TRUE.equals(existing.getHidden())) {
                existing.setHidden(false);
                dirty = true;
            }
            if (Boolean.TRUE.equals(existing.getPermissionControl())) {
                existing.setPermissionControl(false);
                dirty = true;
            }
            if (dirty) {
                routeRepository.save(existing);
            }
            return;
        }

        SysRoute route = new SysRoute();
        route.setTitle("个人信息");
        route.setPath(path);
        route.setViewPath("profile");
        route.setIcon("UserFilled");
        route.setPermission("menu:profile");
        route.setType(RouteType.MENU);
        route.setSort(1);
        route.setStatus(1);
        route.setHidden(false);
        route.setAffix(false);
        route.setPermissionControl(false);
        route.setBuiltIn(true);
        routeRepository.save(route);
    }

    /** 工作台更名为「首页」并换用 HomeFilled 图标 */
    private void ensureHomeRoute() {
        routeRepository
                .findByPath("/dashboard")
                .ifPresent(
                        route -> {
                            boolean dirty = false;
                            if (!"首页".equals(route.getTitle())) {
                                route.setTitle("首页");
                                dirty = true;
                            }
                            if (!"HomeFilled".equals(route.getIcon())) {
                                route.setIcon("HomeFilled");
                                dirty = true;
                            }
                            if (dirty) {
                                routeRepository.save(route);
                            }
                        });
    }

    /** 顶级「系统监控」目录 + 在线用户 / 服务监控 两个子菜单 */
    private void ensureMonitorRoutes() {
        SysRoute monitor = findDirByPermission("menu:monitor");
        if (monitor == null) {
            monitor = saveDirStandalone("系统监控", "Monitor", "menu:monitor", null, 3);
        } else {
            boolean dirty = false;
            if (!"系统监控".equals(monitor.getTitle())) {
                monitor.setTitle("系统监控");
                dirty = true;
            }
            if (!"Monitor".equals(monitor.getIcon())) {
                monitor.setIcon("Monitor");
                dirty = true;
            }
            if (monitor.getParent() != null) {
                monitor.setParent(null);
                dirty = true;
            }
            if (monitor.getSort() == null || monitor.getSort() != 3) {
                monitor.setSort(3);
                dirty = true;
            }
            if (dirty) {
                routeRepository.save(monitor);
            }
        }
        ensureMonitorMenu(
                "/monitor/online",
                "monitor/online",
                "在线用户",
                "Connection",
                "menu:monitor:online",
                monitor,
                1);
        ensureMonitorMenu(
                "/monitor/server",
                "monitor/server",
                "服务监控",
                "Cpu",
                "menu:monitor:server",
                monitor,
                2);
        ensureMonitorMenu(
                "/monitor/redis",
                "monitor/redis",
                "缓存监控",
                "Coin",
                "menu:monitor:redis",
                monitor,
                3);
        ensureMonitorMenu(
                "/monitor/sql", "monitor/sql", "SQL监控", "DataLine", "menu:monitor:sql", monitor, 4);

        // 日志管理挂在系统监控末尾（兼容旧权限码 menu:system:logs）
        SysRoute logsGroup = migrateOrEnsureLogsGroup(monitor);
        setParentAndSort(logsGroup, monitor, 5);
        moveMenu("/system/logs/login", "menu:system:login-log", logsGroup, 1);
        moveMenu("/system/logs/oper", "menu:system:oper-log", logsGroup, 2);
        moveMenu("/system/logs/exception", "menu:system:exception-log", logsGroup, 3);
    }

    /** 将历史「系统管理/日志管理」迁移为「系统监控/日志管理」，或新建 */
    private SysRoute migrateOrEnsureLogsGroup(SysRoute monitor) {
        SysRoute existing = findDirByPermission(PERM_LOGS_GROUP);
        if (existing != null) {
            return existing;
        }
        SysRoute legacy = findDirByPermission("menu:system:logs");
        if (legacy != null) {
            legacy.setPermission(PERM_LOGS_GROUP);
            legacy.setTitle("日志管理");
            legacy.setIcon("Document");
            legacy.setParent(monitor);
            legacy.setSort(5);
            return routeRepository.save(legacy);
        }
        return ensureDir(PERM_LOGS_GROUP, "日志管理", "Document", monitor, 5);
    }

    private void ensureMonitorMenu(
            String path,
            String viewPath,
            String title,
            String icon,
            String permission,
            SysRoute parent,
            int sort) {
        SysRoute existing = routeRepository.findByPath(path).orElse(null);
        if (existing != null) {
            boolean dirty = false;
            if (!title.equals(existing.getTitle())) {
                existing.setTitle(title);
                dirty = true;
            }
            if (!icon.equals(existing.getIcon())) {
                existing.setIcon(icon);
                dirty = true;
            }
            if (!permission.equals(existing.getPermission())) {
                existing.setPermission(permission);
                dirty = true;
            }
            if (!viewPath.equals(existing.getViewPath())) {
                existing.setViewPath(viewPath);
                dirty = true;
            }
            if (parent != null
                    && (existing.getParent() == null
                            || !Objects.equals(existing.getParent().getId(), parent.getId()))) {
                existing.setParent(parent);
                dirty = true;
            }
            if (existing.getSort() == null || existing.getSort() != sort) {
                existing.setSort(sort);
                dirty = true;
            }
            if (!Boolean.TRUE.equals(existing.getPermissionControl())) {
                existing.setPermissionControl(true);
                dirty = true;
            }
            if (dirty) {
                routeRepository.save(existing);
            }
            return;
        }
        SysRoute route = new SysRoute();
        route.setTitle(title);
        route.setPath(path);
        route.setViewPath(viewPath);
        route.setIcon(icon);
        route.setPermission(permission);
        route.setParent(parent);
        route.setType(RouteType.MENU);
        route.setSort(sort);
        route.setStatus(1);
        route.setHidden(false);
        route.setAffix(false);
        route.setPermissionControl(true);
        route.setBuiltIn(true);
        routeRepository.save(route);
    }

    /** 新增「权限内容」菜单路由 */
    private void ensurePermissionContentRoute() {
        String path = "/system/permissions-content";
        if (routeRepository.existsByPath(path)) {
            return;
        }
        SysRoute parent = findRbacGroupRoute();
        if (parent == null) {
            parent = findDirByPermission("menu:system");
        }
        int nextSort =
                routeRepository.findAll().stream()
                                .mapToInt(r -> r.getSort() == null ? 0 : r.getSort())
                                .max()
                                .orElse(0)
                        + 1;

        SysRoute route = new SysRoute();
        route.setTitle("权限内容");
        route.setPath(path);
        route.setViewPath("system/permissions-content");
        route.setIcon("Key");
        route.setPermission("menu:system:permission-content");
        route.setParent(parent);
        route.setType(RouteType.MENU);
        route.setSort(nextSort);
        route.setStatus(1);
        route.setHidden(false);
        route.setAffix(false);
        route.setPermissionControl(true);
        route.setBuiltIn(true);
        routeRepository.save(route);
    }

    private void initRoutes() {
        Map<String, SysRoute> map = new LinkedHashMap<>();
        int sort = 0;

        SysRoute dashboard =
                saveMenu(
                        map,
                        "dashboard",
                        "工作台",
                        "/dashboard",
                        "dashboard",
                        "Odometer",
                        "menu:dashboard",
                        null,
                        ++sort);
        dashboard.setAffix(true);
        routeRepository.save(dashboard);

        SysRoute system = saveDir(map, "system", "系统管理", "Setting", "menu:system", null, ++sort);
        SysRoute orgGroup =
                saveDir(map, "org-group", "组织与账号", "OfficeBuilding", PERM_ORG_GROUP, system, 1);
        SysRoute rbacGroup =
                saveDir(map, "rbac-group", "权限与安全", "Lock", PERM_RBAC_GROUP, system, 2);
        SysRoute contentGroup =
                saveDir(map, "content-group", "内容运营", "Notebook", PERM_CONTENT_GROUP, system, 3);
        saveDir(map, "base-group", "基础数据", "Collection", PERM_BASE_GROUP, system, 4);
        saveDir(map, "settings-group", "系统设置", "Tools", PERM_SETTINGS_GROUP, system, 5);
        saveDir(map, "tools-group", "系统工具", "Suitcase", PERM_TOOLS_GROUP, system, 6);

        saveMenu(map, "users", "用户管理", "/users", "users", "User", "menu:system:user", orgGroup, 1);
        saveMenu(
                map,
                "units",
                "单位管理",
                "/system/units",
                "system/units",
                "OfficeBuilding",
                "menu:system:unit",
                orgGroup,
                2);

        saveMenu(
                map,
                "roles",
                "角色列表",
                "/system/roles",
                "system/roles",
                "Avatar",
                "menu:system:role",
                rbacGroup,
                1);
        saveMenu(
                map,
                "permissions",
                "角色权限",
                "/system/permissions",
                "system/permissions",
                "SetUp",
                "menu:system:permission",
                rbacGroup,
                2);
        saveMenu(
                map,
                "permission-content",
                "权限内容",
                "/system/permissions-content",
                "system/permissions-content",
                "Key",
                "menu:system:permission-content",
                rbacGroup,
                3);
        saveMenu(
                map,
                "routes",
                "路由管理",
                "/system/routes",
                "system/routes",
                "Guide",
                "menu:system:route",
                rbacGroup,
                4);

        saveMenu(
                map,
                "notices",
                "公告管理",
                "/system/notices",
                "system/notices",
                "Bell",
                "menu:system:notice",
                contentGroup,
                1);

        SysRoute personal =
                saveDir(map, "personal", "个人中心", "UserFilled", PERM_PERSONAL_GROUP, null, ++sort);
        SysRoute profile =
                saveMenu(
                        map,
                        "profile",
                        "个人信息",
                        "/profile",
                        "profile",
                        "UserFilled",
                        "menu:profile",
                        personal,
                        1);
        profile.setPermissionControl(false);
        routeRepository.save(profile);
    }

    private void ensureRouteIcons() {
        setIconByPath("/dashboard", "HomeFilled");
        setIconByPermission("menu:system", RouteType.DIR, "Setting");
        setIconByPermission("menu:monitor", RouteType.DIR, "Monitor");
        setIconByPath("/monitor/online", "Connection");
        setIconByPath("/monitor/server", "Cpu");
        setIconByPermission(PERM_ORG_GROUP, RouteType.DIR, "OfficeBuilding");
        setIconByPermission(PERM_RBAC_GROUP, RouteType.DIR, "Lock");
        setIconByPermission(PERM_CONTENT_GROUP, RouteType.DIR, "Notebook");
        setIconByPermission(PERM_BASE_GROUP, RouteType.DIR, "Collection");
        setIconByPermission(PERM_SETTINGS_GROUP, RouteType.DIR, "Tools");
        setIconByPermission(PERM_TOOLS_GROUP, RouteType.DIR, "Suitcase");
        setIconByPermission(PERM_LOGS_GROUP, RouteType.DIR, "Document");
        setIconByPermission(PERM_PERSONAL_GROUP, RouteType.DIR, "UserFilled");
        setIconByPath("/users", "User");
        setIconByPath("/profile", "UserFilled");
        setIconByPath("/system/roles", "Avatar");
        setIconByPath("/system/units", "OfficeBuilding");
        setIconByPath("/system/permissions", "SetUp");
        setIconByPath("/system/routes", "Guide");
        setIconByPath("/system/permissions-content", "Key");
        setIconByPath("/system/notices", "Bell");
        setIconByPath("/system/messages", "Message");
        setIconByPath("/messages/mine", "ChatDotRound");
        setIconByPath("/system/dicts", "Collection");
        setIconByPath("/system/dicts/data", "Collection");
        setIconByPath("/system/login-settings", "PictureFilled");
        setIconByPath("/system/config", "Setting");
        setIconByPath("/system/site-contact", "Phone");
        setIconByPath("/system/security", "Key");
        setIconByPath("/system/files", "FolderOpened");
        setIconByPath("/system/jobs", "Timer");
        setIconByPath("/system/api-docs", "Document");
        setIconByPath("/system/recycle", "Delete");
        setIconByPath("/system/codegen", "MagicStick");
        setIconByPath("/system/logs/login", "Position");
        setIconByPath("/system/logs/oper", "Document");
        setIconByPath("/system/logs/exception", "Warning");
        setIconByPath("/monitor/redis", "Coin");
        setIconByPath("/monitor/sql", "DataLine");
    }

    private void ensureUnitRoute() {
        String path = "/system/units";
        if (routeRepository.existsByPath(path)) {
            return;
        }
        SysRoute parent = findDirByPermission(PERM_ORG_GROUP);
        if (parent == null) {
            parent = findDirByPermission("menu:system");
        }
        SysRoute route = new SysRoute();
        route.setTitle("单位管理");
        route.setPath(path);
        route.setViewPath("system/units");
        route.setIcon("OfficeBuilding");
        route.setPermission("menu:system:unit");
        route.setParent(parent);
        route.setType(RouteType.MENU);
        route.setSort(2);
        route.setStatus(1);
        route.setHidden(false);
        route.setAffix(false);
        route.setPermissionControl(true);
        route.setBuiltIn(true);
        routeRepository.save(route);
    }

    /** 字典管理菜单路由 */
    private void ensureDictRoute() {
        String path = "/system/dicts";
        if (routeRepository.existsByPath(path)) {
            return;
        }
        SysRoute parent = findDirByPermission(PERM_BASE_GROUP);
        if (parent == null) {
            parent = findDirByPermission("menu:system");
        }
        SysRoute route = new SysRoute();
        route.setTitle("字典管理");
        route.setPath(path);
        route.setViewPath("system/dicts");
        route.setIcon("Collection");
        route.setPermission("menu:system:dict");
        route.setParent(parent);
        route.setType(RouteType.MENU);
        route.setSort(1);
        route.setStatus(1);
        route.setHidden(false);
        route.setAffix(false);
        route.setPermissionControl(true);
        route.setBuiltIn(true);
        routeRepository.save(route);
    }

    /** 登录页设置菜单路由 */
    private void ensureLoginPageRoute() {
        String path = "/system/login-settings";
        if (routeRepository.existsByPath(path)) {
            return;
        }
        SysRoute parent = findDirByPermission(PERM_SETTINGS_GROUP);
        if (parent == null) {
            parent = findDirByPermission("menu:system");
        }
        SysRoute route = new SysRoute();
        route.setTitle("登录页设置");
        route.setPath(path);
        route.setViewPath("system/login-settings");
        route.setIcon("PictureFilled");
        route.setPermission("menu:system:login-page");
        route.setParent(parent);
        route.setType(RouteType.MENU);
        route.setSort(1);
        route.setStatus(1);
        route.setHidden(false);
        route.setAffix(false);
        route.setPermissionControl(true);
        route.setBuiltIn(true);
        routeRepository.save(route);
    }

    /** 系统配置菜单路由（与前端 app.ts 对齐） */
    private void ensureSystemConfigRoute() {
        String path = "/system/config";
        if (routeRepository.existsByPath(path)) {
            return;
        }
        SysRoute parent = findDirByPermission(PERM_SETTINGS_GROUP);
        if (parent == null) {
            parent = findDirByPermission("menu:system");
        }
        SysRoute route = new SysRoute();
        route.setTitle("系统配置");
        route.setPath(path);
        route.setViewPath("system/config");
        route.setIcon("Setting");
        route.setPermission("menu:system:config");
        route.setParent(parent);
        route.setType(RouteType.MENU);
        route.setSort(2);
        route.setStatus(1);
        route.setHidden(false);
        route.setAffix(false);
        route.setPermissionControl(true);
        route.setBuiltIn(true);
        routeRepository.save(route);
    }

    /** 联系与捐赠菜单路由 */
    private void ensureSiteContactRoute() {
        String path = "/system/site-contact";
        if (routeRepository.existsByPath(path)) {
            return;
        }
        SysRoute parent = findDirByPermission(PERM_SETTINGS_GROUP);
        if (parent == null) {
            parent = findDirByPermission("menu:system");
        }
        SysRoute route = new SysRoute();
        route.setTitle("联系与捐赠");
        route.setPath(path);
        route.setViewPath("system/site-contact");
        route.setIcon("Phone");
        route.setPermission("menu:system:site-contact");
        route.setParent(parent);
        route.setType(RouteType.MENU);
        route.setSort(4);
        route.setStatus(1);
        route.setHidden(false);
        route.setAffix(false);
        route.setPermissionControl(false);
        route.setBuiltIn(true);
        routeRepository.save(route);
    }

    /** 安全策略菜单路由 */
    private void ensureSecurityPolicyRoute() {
        String path = "/system/security";
        if (routeRepository.existsByPath(path)) {
            return;
        }
        SysRoute parent = findDirByPermission(PERM_SETTINGS_GROUP);
        if (parent == null) {
            parent = findDirByPermission("menu:system");
        }
        SysRoute route = new SysRoute();
        route.setTitle("安全策略");
        route.setPath(path);
        route.setViewPath("system/security");
        route.setIcon("Key");
        route.setPermission("menu:system:security");
        route.setParent(parent);
        route.setType(RouteType.MENU);
        route.setSort(3);
        route.setStatus(1);
        route.setHidden(false);
        route.setAffix(false);
        route.setPermissionControl(true);
        route.setBuiltIn(true);
        routeRepository.save(route);
    }

    /** 字典数据：从字典类型列表下钻的隐藏子路由 */
    private void ensureDictDataRoute() {
        String path = "/system/dicts/data";
        if (routeRepository.existsByPath(path)) {
            return;
        }
        SysRoute parent = routeRepository.findByPath("/system/dicts").orElse(null);
        if (parent == null) {
            parent = findDirByPermission("menu:system");
        }
        SysRoute route = new SysRoute();
        route.setTitle("字典数据");
        route.setPath(path);
        route.setViewPath("system/dicts/data");
        route.setIcon("Collection");
        route.setPermission("menu:system:dict-data");
        route.setParent(parent);
        route.setType(RouteType.MENU);
        route.setSort(1);
        route.setStatus(1);
        route.setHidden(true);
        route.setAffix(false);
        route.setPermissionControl(true);
        route.setBuiltIn(true);
        routeRepository.save(route);
    }

    /** 登录日志菜单路由 */
    private void ensureLoginLogRoute() {
        String path = "/system/logs/login";
        if (routeRepository.existsByPath(path)) {
            return;
        }
        SysRoute parent = findDirByPermission(PERM_LOGS_GROUP);
        if (parent == null) {
            parent = findDirByPermission("menu:system");
        }
        SysRoute route = new SysRoute();
        route.setTitle("登录日志");
        route.setPath(path);
        route.setViewPath("system/logs/login");
        route.setIcon("Position");
        route.setPermission("menu:system:login-log");
        route.setParent(parent);
        route.setType(RouteType.MENU);
        route.setSort(1);
        route.setStatus(1);
        route.setHidden(false);
        route.setAffix(false);
        route.setPermissionControl(true);
        route.setBuiltIn(true);
        routeRepository.save(route);
    }

    /** 操作日志菜单路由 */
    private void ensureOperLogRoute() {
        String path = "/system/logs/oper";
        if (routeRepository.existsByPath(path)) {
            return;
        }
        SysRoute parent = findDirByPermission(PERM_LOGS_GROUP);
        if (parent == null) {
            parent = findDirByPermission("menu:system");
        }
        SysRoute route = new SysRoute();
        route.setTitle("操作日志");
        route.setPath(path);
        route.setViewPath("system/logs/oper");
        route.setIcon("Document");
        route.setPermission("menu:system:oper-log");
        route.setParent(parent);
        route.setType(RouteType.MENU);
        route.setSort(2);
        route.setStatus(1);
        route.setHidden(false);
        route.setAffix(false);
        route.setPermissionControl(true);
        route.setBuiltIn(true);
        routeRepository.save(route);
    }

    private void ensureNoticeRoute() {
        String path = "/system/notices";
        if (routeRepository.existsByPath(path)) {
            return;
        }
        SysRoute parent = findDirByPermission(PERM_CONTENT_GROUP);
        if (parent == null) {
            parent = findDirByPermission("menu:system");
        }
        SysRoute route = new SysRoute();
        route.setTitle("公告管理");
        route.setPath(path);
        route.setViewPath("system/notices");
        route.setIcon("Bell");
        route.setPermission("menu:system:notice");
        route.setParent(parent);
        route.setType(RouteType.MENU);
        route.setSort(1);
        route.setStatus(1);
        route.setHidden(false);
        route.setAffix(false);
        route.setPermissionControl(true);
        route.setBuiltIn(true);
        routeRepository.save(route);
    }

    private void ensureMessageRoute() {
        ensureMenuRoute(
                "/system/messages",
                "system/messages",
                "站内信",
                "Message",
                "menu:system:message",
                PERM_CONTENT_GROUP,
                2);
    }

    private void ensureMineMessageRoute() {
        ensureMenuRoute(
                "/messages/mine",
                "messages/mine",
                "我的消息",
                "ChatDotRound",
                "menu:personal:message",
                PERM_PERSONAL_GROUP,
                2);
    }

    private void ensureExceptionLogRoute() {
        ensureMenuRoute(
                "/system/logs/exception",
                "system/logs/exception",
                "异常日志",
                "Warning",
                "menu:system:exception-log",
                PERM_LOGS_GROUP,
                3);
    }

    private void ensureFileRoute() {
        ensureMenuRoute(
                "/system/files",
                "system/files",
                "文件管理",
                "FolderOpened",
                "menu:system:file",
                PERM_TOOLS_GROUP,
                1);
    }

    private void ensureJobRoute() {
        ensureMenuRoute(
                "/system/jobs",
                "system/jobs",
                "定时任务",
                "Timer",
                "menu:system:job",
                PERM_TOOLS_GROUP,
                2);
    }

    private void ensureJobLogRoute() {
        ensureMenuRoute(
                "/system/jobs/logs",
                "system/jobs/logs",
                "任务日志",
                "Document",
                "menu:system:job-log",
                PERM_TOOLS_GROUP,
                3);
    }

    private void ensurePostRoute() {
        ensureMenuRoute(
                "/system/posts",
                "system/posts",
                "岗位管理",
                "Postcard",
                "menu:system:post",
                PERM_ORG_GROUP,
                3);
    }

    private void ensureApiDocsRoute() {
        ensureMenuRoute(
                "/system/api-docs",
                "system/api-docs",
                "接口文档",
                "Document",
                "menu:system:api-docs",
                PERM_TOOLS_GROUP,
                4);
    }

    private void ensureRecycleRoute() {
        ensureMenuRoute(
                "/system/recycle",
                "system/recycle",
                "回收站",
                "Delete",
                "menu:system:recycle",
                PERM_TOOLS_GROUP,
                5);
    }

    private void ensureCodegenRoute() {
        ensureMenuRoute(
                "/system/codegen",
                "system/codegen",
                "代码生成",
                "MagicStick",
                "menu:system:codegen",
                PERM_TOOLS_GROUP,
                6);
    }

    private void ensureMenuRoute(
            String path,
            String viewPath,
            String title,
            String icon,
            String permission,
            String parentPermission,
            int sort) {
        SysRoute existing = routeRepository.findByPath(path).orElse(null);
        if (existing != null) {
            return;
        }
        SysRoute parent = findDirByPermission(parentPermission);
        if (parent == null && !"menu:system".equals(parentPermission)) {
            parent = findDirByPermission("menu:system");
        }
        SysRoute route = new SysRoute();
        route.setTitle(title);
        route.setPath(path);
        route.setViewPath(viewPath);
        route.setIcon(icon);
        route.setPermission(permission);
        route.setParent(parent);
        route.setType(RouteType.MENU);
        route.setSort(sort);
        route.setStatus(1);
        route.setHidden(false);
        route.setAffix(false);
        route.setPermissionControl(true);
        route.setBuiltIn(true);
        routeRepository.save(route);
    }

    private void setIconByPath(String path, String icon) {
        routeRepository
                .findByPath(path)
                .ifPresent(
                        route -> {
                            if (!icon.equals(route.getIcon())) {
                                route.setIcon(icon);
                                routeRepository.save(route);
                            }
                        });
    }

    private void setIconByPermission(String permission, RouteType type, String icon) {
        routeRepository.findAll().stream()
                .filter(r -> type == r.getType() && permission.equals(r.getPermission()))
                .findFirst()
                .ifPresent(
                        route -> {
                            if (!icon.equals(route.getIcon())) {
                                route.setIcon(icon);
                                routeRepository.save(route);
                            }
                        });
    }

    private void ensurePermissionRouteRenamed() {
        routeRepository.findAll().stream()
                .filter(route -> "/system/permissions".equals(route.getPath()))
                .findFirst()
                .ifPresent(
                        route -> {
                            if (!"角色权限".equals(route.getTitle())) {
                                route.setTitle("角色权限");
                                routeRepository.save(route);
                            }
                        });
    }

    private void syncMenuPermissionsFromRoutes() {
        List<SysRoute> routes =
                routeRepository.findAllWithParent().stream()
                        .filter(r -> StringUtils.hasText(r.getPermission()))
                        .sorted(
                                Comparator.comparingInt((SysRoute r) -> depth(r))
                                        .thenComparing(r -> r.getSort() == null ? 0 : r.getSort())
                                        .thenComparing(SysRoute::getId))
                        .toList();

        Map<String, Permission> byCode = new LinkedHashMap<>();
        for (Permission permission : permissionRepository.findAll()) {
            if (permission.getType() == PermissionType.MENU
                    && StringUtils.hasText(permission.getCode())) {
                byCode.put(permission.getCode(), permission);
            }
        }

        for (SysRoute route : routes) {
            String code = route.getPermission();
            Permission permission = byCode.get(code);
            boolean isNew = permission == null;
            if (isNew) {
                permission = new Permission();
                permission.setCode(code);
                permission.setType(PermissionType.MENU);
                permission.setBuiltIn(Boolean.TRUE.equals(route.getBuiltIn()));
            }
            permission.setName(route.getTitle());
            permission.setPath(route.getPath());
            permission.setSort(route.getSort() != null ? route.getSort() : 0);

            Permission parentPerm = null;
            if (route.getParent() != null
                    && StringUtils.hasText(route.getParent().getPermission())) {
                parentPerm = byCode.get(route.getParent().getPermission());
                if (parentPerm == null) {
                    parentPerm =
                            permissionRepository
                                    .findByCode(route.getParent().getPermission())
                                    .orElse(null);
                }
            }
            if (parentPerm != null && parentPerm.getCode().equals(code)) {
                parentPerm = parentPerm.getParent();
            }
            permission.setParent(parentPerm);

            permission = permissionRepository.save(permission);
            byCode.put(code, permission);
            if (isNew) {
                grantToPrivilegedRoles(permission);
            }
        }
    }

    private int depth(SysRoute route) {
        int d = 0;
        SysRoute p = route.getParent();
        while (p != null) {
            d++;
            p = p.getParent();
        }
        return d;
    }

    private void grantToPrivilegedRoles(Permission permission) {
        for (String roleCode : List.of("SUPER_ADMIN", "ADMIN")) {
            roleRepository
                    .findByCode(roleCode)
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

    private SysRoute saveDirStandalone(
            String title, String icon, String permission, SysRoute parent, int sort) {
        SysRoute route = new SysRoute();
        route.setTitle(title);
        route.setIcon(icon);
        route.setPermission(permission);
        route.setParent(parent);
        route.setType(RouteType.DIR);
        route.setSort(sort);
        route.setStatus(1);
        route.setHidden(false);
        route.setAffix(false);
        route.setPermissionControl(false);
        route.setBuiltIn(true);
        return routeRepository.save(route);
    }

    private SysRoute saveDir(
            Map<String, SysRoute> map,
            String key,
            String title,
            String icon,
            String permission,
            SysRoute parent,
            int sort) {
        SysRoute saved = saveDirStandalone(title, icon, permission, parent, sort);
        map.put(key, saved);
        return saved;
    }

    private SysRoute saveMenu(
            Map<String, SysRoute> map,
            String key,
            String title,
            String path,
            String viewPath,
            String icon,
            String permission,
            SysRoute parent,
            int sort) {
        SysRoute route = new SysRoute();
        route.setTitle(title);
        route.setPath(path);
        route.setViewPath(viewPath);
        route.setIcon(icon);
        route.setPermission(permission);
        route.setParent(parent);
        route.setType(RouteType.MENU);
        route.setSort(sort);
        route.setStatus(1);
        route.setHidden(false);
        route.setAffix(false);
        route.setPermissionControl(false);
        route.setBuiltIn(true);
        SysRoute saved = routeRepository.save(route);
        map.put(key, saved);
        return saved;
    }
}
