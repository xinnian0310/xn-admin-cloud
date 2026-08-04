package com.smartadmin.config;

import com.smartadmin.entity.*;
import com.smartadmin.repository.PermissionRepository;
import com.smartadmin.repository.RoleRepository;
import com.smartadmin.repository.UserRepository;
import com.smartadmin.security.ApiPermissionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

@Component
@Order(1)
@RequiredArgsConstructor
public class RbacInitializer implements CommandLineRunner {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApiPermissionRegistry apiPermissionRegistry;

    @Override
    @Transactional
    public void run(String... args) {
        permissionRepository.migrateLegacySearchAndTableTypes();
        if (permissionRepository.count() == 0) {
            Map<String, Permission> permissionMap = initPermissions();
            initRoles(permissionMap);
        }
        migrateExistingUsers();
        ensureBuiltinRoles();
        ensureSeedAccounts();
        ensureRoutePermissions();
        ensureStandardButtonPermissions();
        ensurePermissionMenuRenamed();
        ensurePermissionContentMenu();
        ensureTableColumnPermissions();
        ensurePageApiPermissions();
        ensureRouteCodegenPermissions();
        ensureNoticePermissions();
        ensureMessagePermissions();
        ensureUnitPermissions();
        ensurePostPermissions();
        ensureMonitorPermissions();
        ensureRedisSqlMonitorPermissions();
        ensureDictPermissions();
        ensureLoginPagePermissions();
        ensureSystemConfigPermissions();
        ensureSecurityPolicyPermissions();
        ensureLoginLogPermissions();
        ensureOperLogPermissions();
        ensureExceptionLogPermissions();
        ensureFilePermissions();
        ensureRecyclePermissions();
        ensureJobPermissions();
        ensureJobLogPermissions();
        ensureApiDocsPermissions();
        ensureCodegenPermissions();
        removeLegacyPermissionIfEmpty("menu:system:logs");
        ensureRoleDataScopes();
        // 启动补齐权限后刷新接口注册表，避免新 API 未登记被守卫拦截
        permissionRepository.flush();
        apiPermissionRegistry.reload();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    apiPermissionRegistry.reload();
                }
            });
        }
    }

    /** 补齐角色数据权限：SUPER_ADMIN=ALL，其余默认 UNIT_AND_CHILDREN */
    private void ensureRoleDataScopes() {
        for (Role role : roleRepository.findAll()) {
            if (role.getDataScope() != null) {
                if ("SUPER_ADMIN".equals(role.getCode()) && role.getDataScope() != DataScope.ALL) {
                    role.setDataScope(DataScope.ALL);
                    roleRepository.save(role);
                }
                continue;
            }
            if ("SUPER_ADMIN".equals(role.getCode()) || "ADMIN".equals(role.getCode())) {
                role.setDataScope(DataScope.ALL);
            } else {
                role.setDataScope(DataScope.UNIT_AND_CHILDREN);
            }
            roleRepository.save(role);
        }
    }

    /** 系统配置：单例表单，查看 + 保存 + 上传 */
    private void ensureSystemConfigPermissions() {
        Permission settings = permissionRepository.findByCode("menu:system:settings")
                .or(() -> permissionRepository.findByCode("menu:system"))
                .orElse(null);
        Permission menu = ensureMenuPermission(
                "menu:system:config", "系统配置", "/system/config", settings, 2);
        upsertButton("system-config:view", "查看", menu, 1);
        upsertButton("system-config:update", "保存", menu, 2);
        for (String code : List.of("system-config:view", "system-config:update")) {
            permissionRepository.findByCode(code).ifPresent(this::grantToPrivilegedRoles);
        }
        upsertApi("api:GET:/api/system-config", "系统配置查询", "GET", "/api/system-config", menu, 1);
        upsertApi("api:PUT:/api/system-config", "系统配置保存", "PUT", "/api/system-config", menu, 2);
        upsertApi("api:POST:/api/system-config/upload", "系统配置资源上传", "POST", "/api/system-config/upload", menu, 3);
        // /public 走 permitAll，不登记到角色
    }

    /** 安全策略：登录锁定 / 限流 / 验证码 TTL，及锁定账号解锁 */
    private void ensureSecurityPolicyPermissions() {
        Permission settings = permissionRepository.findByCode("menu:system:settings")
                .or(() -> permissionRepository.findByCode("menu:system"))
                .orElse(null);
        Permission menu = ensureMenuPermission(
                "menu:system:security", "安全策略", "/system/security", settings, 3);
        upsertButton("security-policy:refresh", "刷新", menu, 1);
        upsertButton("security-policy:update", "保存", menu, 2);
        upsertTableButton("security-policy:table-unlock", "解锁", menu, 1);
        for (String code : List.of("security-policy:refresh", "security-policy:update", "security-policy:table-unlock")) {
            permissionRepository.findByCode(code).ifPresent(this::grantToPrivilegedRoles);
        }
        // 兼容旧权限码
        removeObsoletePermission("security-policy:view");
        removeObsoletePermission("security-policy:unlock");
        upsertApi("api:GET:/api/security-policy", "安全策略查询", "GET", "/api/security-policy", menu, 1);
        upsertApi("api:PUT:/api/security-policy", "安全策略保存", "PUT", "/api/security-policy", menu, 2);
        upsertApi("api:GET:/api/security-policy/locks", "锁定账号列表", "GET", "/api/security-policy/locks", menu, 3);
        upsertApi("api:DELETE:/api/security-policy/locks/{username}", "解锁账号", "DELETE",
                "/api/security-policy/locks/{username}", menu, 4);
    }

    /** 「日志管理」分组目录：挂在系统监控下，登录日志 / 操作日志 挂靠于此 */
    private Permission ensureLogsGroup() {
        Permission monitor = permissionRepository.findByCode("menu:monitor").orElse(null);
        return ensureMenuPermission("menu:monitor:logs", "日志管理", null, monitor, 5);
    }

    /** 删除已无子权限的历史菜单权限码（如 menu:system:logs 迁走后） */
    private void removeLegacyPermissionIfEmpty(String legacyCode) {
        permissionRepository.findByCode(legacyCode).ifPresent(perm -> {
            boolean hasChildren = permissionRepository.findAll().stream()
                    .anyMatch(p -> p.getParent() != null && p.getParent().getId().equals(perm.getId()));
            if (hasChildren) {
                return;
            }
            for (Role role : roleRepository.findAll()) {
                Role managed = roleRepository.findByIdWithPermissions(role.getId()).orElse(role);
                Set<Permission> perms = new HashSet<>(managed.getPermissions() == null ? Set.of() : managed.getPermissions());
                if (perms.removeIf(p -> legacyCode.equals(p.getCode()))) {
                    managed.setPermissions(perms);
                    roleRepository.save(managed);
                }
            }
            permissionRepository.delete(perm);
        });
    }

    /** 字典管理：挂在「基础数据」下；含隐藏的字典数据子菜单 */
    private void ensureDictPermissions() {
        Permission base = permissionRepository.findByCode("menu:system:base")
                .or(() -> permissionRepository.findByCode("menu:system"))
                .orElse(null);
        Permission menu = ensureMenuPermission("menu:system:dict", "字典管理", "/system/dicts", base, 1);
        ensureDualCrudButtons(menu, "dict-type", false, false);
        upsertTableButton("dict-type:table-data", "字典数据", menu, 4);
        permissionRepository.findByCode("dict-type:table-data").ifPresent(this::grantToPrivilegedRoles);
        upsertApi("api:GET:/api/dict-types", "字典类型列表接口", "GET", "/api/dict-types", menu, 1);
        upsertApi("api:GET:/api/dict-types/options", "字典类型选项", "GET", "/api/dict-types/options", menu, 2);
        upsertApi("api:GET:/api/dict-types/{id}", "字典类型详情接口", "GET", "/api/dict-types/{id}", menu, 3);
        upsertApi("api:POST:/api/dict-types", "创建字典类型接口", "POST", "/api/dict-types", menu, 4);
        upsertApi("api:PUT:/api/dict-types/{id}", "更新字典类型接口", "PUT", "/api/dict-types/{id}", menu, 5);
        upsertApi("api:DELETE:/api/dict-types/{id}", "删除字典类型接口", "DELETE", "/api/dict-types/{id}", menu, 6);
        upsertApi("api:POST:/api/dict-types/batch-delete", "批量删除字典类型", "POST", "/api/dict-types/batch-delete", menu, 7);

        Permission dataMenu = ensureMenuPermission("menu:system:dict-data", "字典数据", "/system/dicts/data", menu, 1);
        ensureDualCrudButtons(dataMenu, "dict-data", false, false);
        upsertApi("api:GET:/api/dict-data", "字典数据列表接口", "GET", "/api/dict-data", dataMenu, 1);
        upsertApi("api:GET:/api/dict-data/type/{dictType}", "按类型取字典数据", "GET", "/api/dict-data/type/{dictType}", dataMenu, 2);
        upsertApi("api:GET:/api/dict-data/{id}", "字典数据详情接口", "GET", "/api/dict-data/{id}", dataMenu, 3);
        upsertApi("api:POST:/api/dict-data", "创建字典数据接口", "POST", "/api/dict-data", dataMenu, 4);
        upsertApi("api:PUT:/api/dict-data/{id}", "更新字典数据接口", "PUT", "/api/dict-data/{id}", dataMenu, 5);
        upsertApi("api:DELETE:/api/dict-data/{id}", "删除字典数据接口", "DELETE", "/api/dict-data/{id}", dataMenu, 6);
        upsertApi("api:POST:/api/dict-data/batch-delete", "批量删除字典数据", "POST", "/api/dict-data/batch-delete", dataMenu, 7);
        // 字典数据按类型查询：供全站任意页面动态取字典项渲染下拉/标签，全部启用角色可读
        grantApiToAllRoles("api:GET:/api/dict-data/type/{dictType}");
    }

    /** 登录页设置：多套配置，同时仅允许启用一套；公开查询接口走 permitAll，不登记到角色 */
    private void ensureLoginPagePermissions() {
        Permission settings = permissionRepository.findByCode("menu:system:settings")
                .or(() -> permissionRepository.findByCode("menu:system"))
                .orElse(null);
        Permission menu = ensureMenuPermission(
                "menu:system:login-page", "登录页设置", "/system/login-settings", settings, 1);
        ensureDualCrudButtons(menu, "login-page", false, false);
        upsertTableButton("login-page:table-enable", "启用", menu, 4);
        upsertTableButton("login-page:table-disable", "停用", menu, 5);
        for (String code : List.of("login-page:table-enable", "login-page:table-disable")) {
            permissionRepository.findByCode(code).ifPresent(this::grantToPrivilegedRoles);
        }

        upsertApi("api:GET:/api/login-page-configs", "登录页配置列表", "GET", "/api/login-page-configs", menu, 1);
        upsertApi("api:GET:/api/login-page-configs/{id}", "登录页配置详情", "GET", "/api/login-page-configs/{id}", menu, 2);
        upsertApi("api:POST:/api/login-page-configs", "创建登录页配置", "POST", "/api/login-page-configs", menu, 3);
        upsertApi("api:PUT:/api/login-page-configs/{id}", "更新登录页配置", "PUT", "/api/login-page-configs/{id}", menu, 4);
        upsertApi("api:PUT:/api/login-page-configs/{id}/status", "启停登录页配置", "PUT", "/api/login-page-configs/{id}/status", menu, 5);
        upsertApi("api:DELETE:/api/login-page-configs/{id}", "删除登录页配置", "DELETE", "/api/login-page-configs/{id}", menu, 6);
        upsertApi("api:POST:/api/login-page-configs/batch-delete", "批量删除登录页配置", "POST", "/api/login-page-configs/batch-delete", menu, 7);
        upsertApi("api:POST:/api/login-page-configs/upload", "上传登录页背景图", "POST", "/api/login-page-configs/upload", menu, 8);
    }

    /** 登录日志：只读 + 删除/清空/导出，挂在「日志管理」下 */
    private void ensureLoginLogPermissions() {
        Permission logs = ensureLogsGroup();
        Permission menu = ensureMenuPermission("menu:system:login-log", "登录日志", "/system/logs/login", logs, 1);
        upsertButton("loginlog:delete", "删除", menu, 1);
        upsertButton("loginlog:clean", "清空", menu, 2);
        upsertButton("loginlog:export", "导出", menu, 3);
        upsertTableButton("loginlog:table-delete", "删除", menu, 1);
        for (String code : List.of("loginlog:delete", "loginlog:clean", "loginlog:export", "loginlog:table-delete")) {
            permissionRepository.findByCode(code).ifPresent(this::grantToPrivilegedRoles);
        }
        upsertApi("api:GET:/api/logs/login", "登录日志列表接口", "GET", "/api/logs/login", menu, 1);
        upsertApi("api:DELETE:/api/logs/login/{id}", "删除登录日志接口", "DELETE", "/api/logs/login/{id}", menu, 2);
        upsertApi("api:POST:/api/logs/login/batch-delete", "批量删除登录日志", "POST", "/api/logs/login/batch-delete", menu, 3);
        upsertApi("api:DELETE:/api/logs/login/clean", "清空登录日志接口", "DELETE", "/api/logs/login/clean", menu, 4);
        upsertApi("api:GET:/api/logs/login/export", "导出登录日志", "GET", "/api/logs/login/export", menu, 5);
    }

    /** 操作日志：只读 + 详情/删除/清空/导出，挂在「日志管理」下 */
    private void ensureOperLogPermissions() {
        Permission logs = ensureLogsGroup();
        Permission menu = ensureMenuPermission("menu:system:oper-log", "操作日志", "/system/logs/oper", logs, 2);
        upsertButton("operlog:delete", "删除", menu, 1);
        upsertButton("operlog:clean", "清空", menu, 2);
        upsertButton("operlog:export", "导出", menu, 3);
        upsertTableButton("operlog:table-delete", "删除", menu, 1);
        upsertTableButton("operlog:table-view", "详情", menu, 2);
        for (String code : List.of(
                "operlog:delete", "operlog:clean", "operlog:export",
                "operlog:table-delete", "operlog:table-view"
        )) {
            permissionRepository.findByCode(code).ifPresent(this::grantToPrivilegedRoles);
        }
        upsertApi("api:GET:/api/logs/oper", "操作日志列表接口", "GET", "/api/logs/oper", menu, 1);
        upsertApi("api:GET:/api/logs/oper/{id}", "操作日志详情接口", "GET", "/api/logs/oper/{id}", menu, 2);
        upsertApi("api:DELETE:/api/logs/oper/{id}", "删除操作日志接口", "DELETE", "/api/logs/oper/{id}", menu, 3);
        upsertApi("api:POST:/api/logs/oper/batch-delete", "批量删除操作日志", "POST", "/api/logs/oper/batch-delete", menu, 4);
        upsertApi("api:DELETE:/api/logs/oper/clean", "清空操作日志接口", "DELETE", "/api/logs/oper/clean", menu, 5);
        upsertApi("api:GET:/api/logs/oper/export", "导出操作日志", "GET", "/api/logs/oper/export", menu, 6);
    }

    /** 授权指定接口权限给全部启用角色（如全站通用的只读接口） */
    private void grantApiToAllRoles(String code) {
        Permission permission = permissionRepository.findByCode(code).orElse(null);
        if (permission == null) {
            return;
        }
        for (Role role : roleRepository.findAll()) {
            Role managed = roleRepository.findByIdWithPermissions(role.getId()).orElse(role);
            Set<Permission> perms = new HashSet<>(managed.getPermissions() == null ? Set.of() : managed.getPermissions());
            if (perms.add(permission)) {
                managed.setPermissions(perms);
                roleRepository.save(managed);
            }
        }
    }

    /** 系统监控：顶级菜单 + 在线用户/服务监控子菜单 + 接口权限 */
    private void ensureMonitorPermissions() {
        Permission monitor = permissionRepository.findByCode("menu:monitor").orElse(null);
        if (monitor == null) {
            int nextSort = permissionRepository.findAll().stream()
                    .mapToInt(p -> p.getSort() == null ? 0 : p.getSort())
                    .max()
                    .orElse(0) + 1;
            Permission created = new Permission();
            created.setCode("menu:monitor");
            created.setName("系统监控");
            created.setType(PermissionType.MENU);
            created.setParent(null);
            created.setSort(nextSort);
            created.setBuiltIn(true);
            monitor = permissionRepository.save(created);
        }
        grantToPrivilegedRoles(monitor);

        Permission online = ensureMenuPermission("menu:monitor:online", "在线用户", "/monitor/online", monitor, 1);
        Permission server = ensureMenuPermission("menu:monitor:server", "服务监控", "/monitor/server", monitor, 2);

        // 工具栏 + 表格操作均为「下线」
        upsertButton("online:offline", "下线", online, 1);
        upsertTableButton("online:table-offline", "下线", online, 1);
        for (String code : List.of("online:offline", "online:table-offline")) {
            permissionRepository.findByCode(code).ifPresent(this::grantToPrivilegedRoles);
        }
        removeObsoletePermission("online:refresh");
        removeObsoletePermission("online:table-kick");

        // 服务监控：刷新 + 基础设施重启（页面硬编码按钮）
        upsertButton("server:refresh", "刷新", server, 1);
        upsertButton("server:restart", "重启", server, 2);
        for (String code : List.of("server:refresh", "server:restart")) {
            permissionRepository.findByCode(code).ifPresent(this::grantToPrivilegedRoles);
        }

        upsertApi("api:GET:/api/monitor/online", "在线用户列表", "GET", "/api/monitor/online", online, 1);
        upsertApi("api:POST:/api/monitor/online/{userId}/kick", "强制下线", "POST", "/api/monitor/online/{userId}/kick", online, 2);
        upsertApi("api:GET:/api/monitor/server", "服务监控数据", "GET", "/api/monitor/server", server, 1);
        upsertApi("api:GET:/api/monitor/infra", "基础设施状态", "GET", "/api/monitor/infra", server, 2);
        upsertApi("api:POST:/api/monitor/infra/{name}/restart", "基础设施一键重启", "POST",
                "/api/monitor/infra/{name}/restart", server, 3);
    }

    private void ensureRedisSqlMonitorPermissions() {
        Permission monitor = permissionRepository.findByCode("menu:monitor").orElse(null);
        Permission redis = ensureMenuPermission("menu:monitor:redis", "缓存监控", "/monitor/redis", monitor, 3);
        Permission sql = ensureMenuPermission("menu:monitor:sql", "SQL监控", "/monitor/sql", monitor, 4);

        // 删改查 + 表格操作
        ensureViewUpdateDeleteButtons(redis, "redis");
        ensureViewUpdateDeleteButtons(sql, "sql");
        removeObsoletePermission("redis:refresh");
        removeObsoletePermission("redis:flush");
        removeObsoletePermission("sql:refresh");
        removeObsoletePermission("sql:clean");

        upsertApi("api:GET:/api/monitor/redis", "Redis监控数据", "GET", "/api/monitor/redis", redis, 1);
        upsertApi("api:DELETE:/api/monitor/redis/keys", "删除Redis键", "DELETE", "/api/monitor/redis/keys", redis, 2);
        upsertApi("api:DELETE:/api/monitor/redis/flush", "清空Redis库", "DELETE", "/api/monitor/redis/flush", redis, 3);

        upsertApi("api:GET:/api/monitor/sql", "SQL监控数据", "GET", "/api/monitor/sql", sql, 1);
        upsertApi("api:DELETE:/api/monitor/sql/clean", "清空SQL监控", "DELETE", "/api/monitor/sql/clean", sql, 2);
        upsertApi("api:DELETE:/api/monitor/sql/records/{id}", "删除SQL记录", "DELETE",
                "/api/monitor/sql/records/{id}", sql, 3);
    }

    private void ensureMessagePermissions() {
        Permission menuContent = permissionRepository.findByCode("menu:system:content").orElse(null);
        Permission menuSystem = permissionRepository.findByCode("menu:system").orElse(null);
        Permission parent = menuContent != null ? menuContent : menuSystem;
        Permission menu = ensureMenuPermission("menu:system:message", "站内信", "/system/messages", parent, 2);
        ensureDualCrudButtons(menu, "message", false, false);
        upsertButton("message:send", "发送", menu, 5);
        upsertTableButton("message:table-send", "发送", menu, 4);
        upsertTableButton("message:table-readers", "已读明细", menu, 5);
        for (String code : List.of("message:send", "message:table-send", "message:table-readers")) {
            permissionRepository.findByCode(code).ifPresent(this::grantToPrivilegedRoles);
        }

        upsertApi("api:GET:/api/messages", "站内信列表", "GET", "/api/messages", menu, 1);
        upsertApi("api:GET:/api/messages/{id}", "站内信详情", "GET", "/api/messages/{id}", menu, 2);
        upsertApi("api:POST:/api/messages", "创建站内信", "POST", "/api/messages", menu, 3);
        upsertApi("api:PUT:/api/messages/{id}", "更新站内信", "PUT", "/api/messages/{id}", menu, 4);
        upsertApi("api:DELETE:/api/messages/{id}", "删除站内信", "DELETE", "/api/messages/{id}", menu, 5);
        upsertApi("api:POST:/api/messages/batch-delete", "批量删除站内信", "POST", "/api/messages/batch-delete", menu, 6);
        upsertApi("api:POST:/api/messages/{id}/send", "发送站内信", "POST", "/api/messages/{id}/send", menu, 7);
        upsertApi("api:GET:/api/messages/{id}/readers", "站内信已读明细", "GET", "/api/messages/{id}/readers", menu, 8);

        Permission personal = permissionRepository.findByCode("menu:personal").orElse(menuSystem);
        Permission mineMenu = ensureMenuPermission("menu:personal:message", "我的消息", "/messages/mine", personal, 2);
        grantToPrivilegedRoles(mineMenu);
        // 查看 / 删除（工具栏 + 表格）
        upsertButton("personal-message:view", "查看", mineMenu, 1);
        upsertButton("personal-message:delete", "删除", mineMenu, 2);
        upsertTableButton("personal-message:table-view", "查看", mineMenu, 1);
        upsertTableButton("personal-message:table-delete", "删除", mineMenu, 2);
        for (String code : List.of(
                "personal-message:view", "personal-message:delete",
                "personal-message:table-view", "personal-message:table-delete")) {
            permissionRepository.findByCode(code).ifPresent(this::grantToPrivilegedRoles);
        }
        removeObsoletePermission("personal-message:refresh");
        upsertApi("api:GET:/api/messages/mine", "我的站内信", "GET", "/api/messages/mine", mineMenu, 1);
        upsertApi("api:POST:/api/messages/{id}/read", "标记站内信已读", "POST", "/api/messages/{id}/read", mineMenu, 2);
        upsertApi("api:GET:/api/messages/unread-count", "未读站内信数量", "GET", "/api/messages/unread-count", mineMenu, 3);
        upsertApi("api:DELETE:/api/messages/mine/{id}", "删除我的消息", "DELETE", "/api/messages/mine/{id}", mineMenu, 4);
        upsertApi("api:POST:/api/messages/mine/batch-delete", "批量删除我的消息", "POST",
                "/api/messages/mine/batch-delete", mineMenu, 5);
        grantMessageInboxToAllRoles();
    }

    private void grantMessageInboxToAllRoles() {
        for (String code : List.of(
                "api:GET:/api/messages/mine",
                "api:POST:/api/messages/{id}/read",
                "api:GET:/api/messages/unread-count",
                "api:DELETE:/api/messages/mine/{id}",
                "api:POST:/api/messages/mine/batch-delete"
        )) {
            grantApiToAllRoles(code);
        }
    }

    private void ensureExceptionLogPermissions() {
        Permission logs = ensureLogsGroup();
        Permission menu = ensureMenuPermission("menu:system:exception-log", "异常日志", "/system/logs/exception", logs, 3);
        upsertButton("exlog:delete", "删除", menu, 1);
        upsertButton("exlog:clean", "清空", menu, 2);
        upsertButton("exlog:export", "导出", menu, 3);
        upsertTableButton("exlog:table-delete", "删除", menu, 1);
        upsertTableButton("exlog:table-view", "详情", menu, 2);
        for (String code : List.of(
                "exlog:delete", "exlog:clean", "exlog:export",
                "exlog:table-delete", "exlog:table-view"
        )) {
            permissionRepository.findByCode(code).ifPresent(this::grantToPrivilegedRoles);
        }
        upsertApi("api:GET:/api/logs/exception", "异常日志列表", "GET", "/api/logs/exception", menu, 1);
        upsertApi("api:GET:/api/logs/exception/{id}", "异常日志详情", "GET", "/api/logs/exception/{id}", menu, 2);
        upsertApi("api:DELETE:/api/logs/exception/{id}", "删除异常日志", "DELETE", "/api/logs/exception/{id}", menu, 3);
        upsertApi("api:POST:/api/logs/exception/batch-delete", "批量删除异常日志", "POST", "/api/logs/exception/batch-delete", menu, 4);
        upsertApi("api:DELETE:/api/logs/exception/clean", "清空异常日志", "DELETE", "/api/logs/exception/clean", menu, 5);
        upsertApi("api:GET:/api/logs/exception/export", "导出异常日志", "GET", "/api/logs/exception/export", menu, 6);
    }

    private void ensureFilePermissions() {
        Permission tools = permissionRepository.findByCode("menu:system:tools")
                .or(() -> permissionRepository.findByCode("menu:system"))
                .orElse(null);
        Permission menu = ensureMenuPermission("menu:system:file", "文件管理", "/system/files", tools, 1);
        migrateFileViewToRefresh();
        upsertButton("file:refresh", "刷新", menu, 1);
        upsertButton("file:mkdir", "新建目录", menu, 2);
        upsertButton("file:upload", "上传", menu, 3);
        upsertButton("file:delete", "删除", menu, 4);
        migrateFileTablePreviewToView();
        removeObsoletePermission("file:table-open");
        upsertTableButton("file:table-enter", "进入", menu, 1);
        upsertTableButton("file:table-view", "查看", menu, 2);
        upsertTableButton("file:table-delete", "删除", menu, 3);
        for (String code : List.of(
                "file:refresh", "file:mkdir", "file:upload", "file:delete",
                "file:table-enter", "file:table-view", "file:table-delete"
        )) {
            permissionRepository.findByCode(code).ifPresent(this::grantToPrivilegedRoles);
        }
        upsertApi("api:GET:/api/files", "文件列表", "GET", "/api/files", menu, 1);
        upsertApi("api:GET:/api/files/browse", "按路径浏览文件", "GET", "/api/files/browse", menu, 2);
        upsertApi("api:GET:/api/files/tree", "文件目录树", "GET", "/api/files/tree", menu, 3);
        upsertApi("api:POST:/api/files/upload", "上传文件", "POST", "/api/files/upload", menu, 4);
        upsertApi("api:POST:/api/files/mkdir", "新建目录", "POST", "/api/files/mkdir", menu, 5);
        upsertApi("api:DELETE:/api/files", "删除文件", "DELETE", "/api/files", menu, 6);
    }

    /** 回收站：用户 / 文件软删除后进入此处，可恢复或彻底删除 */
    private void ensureRecyclePermissions() {
        Permission tools = permissionRepository.findByCode("menu:system:tools")
                .or(() -> permissionRepository.findByCode("menu:system"))
                .orElse(null);
        Permission menu = ensureMenuPermission("menu:system:recycle", "回收站", "/system/recycle", tools, 5);
        upsertButton("recycle:restore", "恢复", menu, 1);
        upsertButton("recycle:purge", "彻底删除", menu, 2);
        upsertButton("recycle:clean", "清空", menu, 3);
        upsertTableButton("recycle:table-restore", "恢复", menu, 1);
        upsertTableButton("recycle:table-purge", "彻底删除", menu, 2);
        for (String code : List.of(
                "recycle:restore", "recycle:purge", "recycle:clean",
                "recycle:table-restore", "recycle:table-purge"
        )) {
            permissionRepository.findByCode(code).ifPresent(this::grantToPrivilegedRoles);
        }
        upsertApi("api:GET:/api/recycle", "回收站列表", "GET", "/api/recycle", menu, 1);
        upsertApi("api:POST:/api/recycle/{id}/restore", "恢复回收站项", "POST", "/api/recycle/{id}/restore", menu, 2);
        upsertApi("api:DELETE:/api/recycle/{id}", "彻底删除回收站项", "DELETE", "/api/recycle/{id}", menu, 3);
        upsertApi("api:POST:/api/recycle/batch-delete", "批量彻底删除", "POST", "/api/recycle/batch-delete", menu, 4);
        upsertApi("api:DELETE:/api/recycle/clean", "清空回收站", "DELETE", "/api/recycle/clean", menu, 5);
    }

    /** 旧版 file:view 迁移为 file:refresh，避免工具栏仍出现「查看」 */
    private void migrateFileViewToRefresh() {
        migratePermissionCode("file:view", "file:refresh", "刷新");
    }

    /** 行操作「预览」迁移为「查看」 */
    private void migrateFileTablePreviewToView() {
        migratePermissionCode("file:table-preview", "file:table-view", "查看");
    }

    private void migratePermissionCode(String oldCode, String newCode, String newName) {
        Permission oldPerm = permissionRepository.findByCode(oldCode).orElse(null);
        if (oldPerm == null) {
            return;
        }
        Permission newPerm = permissionRepository.findByCode(newCode).orElse(null);
        if (newPerm == null) {
            oldPerm.setCode(newCode);
            oldPerm.setName(newName);
            applyButtonMeta(oldPerm, newCode);
            if (oldPerm.getType() == PermissionType.TABLE_BUTTON) {
                oldPerm.setIcon(null);
            }
            permissionRepository.save(oldPerm);
            return;
        }
        roleRepository.findAll().forEach(role -> {
            if (role.getPermissions().contains(oldPerm)) {
                Set<Permission> perms = new HashSet<>(role.getPermissions());
                perms.remove(oldPerm);
                perms.add(newPerm);
                role.setPermissions(perms);
                roleRepository.save(role);
            }
        });
        permissionRepository.delete(oldPerm);
    }

    private void removeObsoletePermission(String code) {
        permissionRepository.findByCode(code).ifPresent(old -> {
            roleRepository.findAll().forEach(role -> {
                if (role.getPermissions().contains(old)) {
                    Set<Permission> perms = new HashSet<>(role.getPermissions());
                    perms.remove(old);
                    role.setPermissions(perms);
                    roleRepository.save(role);
                }
            });
            permissionRepository.delete(old);
        });
    }

    private void ensureJobPermissions() {
        Permission tools = permissionRepository.findByCode("menu:system:tools")
                .or(() -> permissionRepository.findByCode("menu:system"))
                .orElse(null);
        Permission menu = ensureMenuPermission("menu:system:job", "定时任务", "/system/jobs", tools, 2);
        ensureDualCrudButtons(menu, "job", false, false);
        upsertButton("job:run", "执行一次", menu, 5);
        upsertTableButton("job:table-run", "执行", menu, 4);
        for (String code : List.of("job:run", "job:table-run")) {
            permissionRepository.findByCode(code).ifPresent(this::grantToPrivilegedRoles);
        }
        upsertApi("api:GET:/api/jobs", "定时任务列表", "GET", "/api/jobs", menu, 1);
        upsertApi("api:GET:/api/jobs/{id}", "定时任务详情", "GET", "/api/jobs/{id}", menu, 2);
        upsertApi("api:POST:/api/jobs", "创建定时任务", "POST", "/api/jobs", menu, 3);
        upsertApi("api:PUT:/api/jobs/{id}", "更新定时任务", "PUT", "/api/jobs/{id}", menu, 4);
        upsertApi("api:DELETE:/api/jobs/{id}", "删除定时任务", "DELETE", "/api/jobs/{id}", menu, 5);
        upsertApi("api:POST:/api/jobs/batch-delete", "批量删除定时任务", "POST", "/api/jobs/batch-delete", menu, 6);
        upsertApi("api:PUT:/api/jobs/{id}/status", "启停定时任务", "PUT", "/api/jobs/{id}/status", menu, 7);
        upsertApi("api:POST:/api/jobs/{id}/run", "执行定时任务", "POST", "/api/jobs/{id}/run", menu, 8);
    }

    private void ensureApiDocsPermissions() {
        Permission tools = permissionRepository.findByCode("menu:system:tools")
                .or(() -> permissionRepository.findByCode("menu:system"))
                .orElse(null);
        Permission menu = ensureMenuPermission("menu:system:api-docs", "接口文档", "/system/api-docs", tools, 4);
        upsertButton("api-docs:view", "查看", menu, 1);
        permissionRepository.findByCode("api-docs:view").ifPresent(this::grantToPrivilegedRoles);
    }

    /** 表驱动代码生成 */
    private void ensureCodegenPermissions() {
        Permission tools = permissionRepository.findByCode("menu:system:tools")
                .or(() -> permissionRepository.findByCode("menu:system"))
                .orElse(null);
        Permission menu = ensureMenuPermission("menu:system:codegen", "代码生成", "/system/codegen", tools, 6);
        // 工具栏刷新 + 行内生成（对齐 xnButton / xnTable 标准列表）
        upsertButton("codegen:refresh", "刷新", menu, 1);
        upsertTableButton("codegen:generate", "生成", menu, 2);
        permissionRepository.findByCode("codegen:refresh").ifPresent(this::grantToPrivilegedRoles);
        permissionRepository.findByCode("codegen:generate").ifPresent(this::grantToPrivilegedRoles);
        upsertApi("api:GET:/api/codegen/tables", "代码生成-表列表", "GET", "/api/codegen/tables", menu, 1);
        upsertApi("api:GET:/api/codegen/tables/{tableName}/columns", "代码生成-表字段", "GET",
                "/api/codegen/tables/{tableName}/columns", menu, 2);
        upsertApi("api:POST:/api/codegen/preview", "代码生成-预览", "POST", "/api/codegen/preview", menu, 3);
        upsertApi("api:POST:/api/codegen/generate", "代码生成-生成", "POST", "/api/codegen/generate", menu, 4);
    }

    private Permission ensureMenuPermission(String code, String name, String path, Permission parent, int sort) {
        Permission menu = permissionRepository.findByCode(code).orElse(null);
        if (menu == null) {
            Permission created = new Permission();
            created.setCode(code);
            created.setName(name);
            created.setType(PermissionType.MENU);
            created.setPath(path);
            created.setParent(parent);
            created.setSort(sort);
            created.setBuiltIn(true);
            menu = permissionRepository.save(created);
        } else {
            boolean dirty = false;
            if (parent != null && (menu.getParent() == null || !menu.getParent().getId().equals(parent.getId()))) {
                menu.setParent(parent);
                dirty = true;
            }
            if (menu.getSort() == null || menu.getSort() != sort) {
                menu.setSort(sort);
                dirty = true;
            }
            if (name != null && !name.equals(menu.getName())) {
                menu.setName(name);
                dirty = true;
            }
            if (dirty) {
                permissionRepository.save(menu);
            }
        }
        grantToPrivilegedRoles(menu);
        return menu;
    }

    /** 岗位管理：挂在「组织与账号」下 */
    private void ensurePostPermissions() {
        Permission menuOrg = permissionRepository.findByCode("menu:system:org").orElse(null);
        Permission parent = menuOrg != null
                ? menuOrg
                : permissionRepository.findByCode("menu:system").orElse(null);
        Permission menu = ensureMenuPermission("menu:system:post", "岗位管理", "/system/posts", parent, 3);
        ensureDualCrudButtons(menu, "post", false, false);
        upsertButton("post:import", "导入", menu, 5);
        upsertButton("post:export", "导出", menu, 6);
        for (String code : List.of("post:import", "post:export")) {
            permissionRepository.findByCode(code).ifPresent(this::grantToPrivilegedRoles);
        }
        upsertApi("api:GET:/api/posts", "岗位列表", "GET", "/api/posts", menu, 1);
        upsertApi("api:GET:/api/posts/options", "岗位选项", "GET", "/api/posts/options", menu, 2);
        upsertApi("api:GET:/api/posts/export", "导出岗位", "GET", "/api/posts/export", menu, 3);
        upsertApi("api:GET:/api/posts/{id}", "岗位详情", "GET", "/api/posts/{id}", menu, 4);
        upsertApi("api:POST:/api/posts", "创建岗位", "POST", "/api/posts", menu, 5);
        upsertApi("api:PUT:/api/posts/{id}", "更新岗位", "PUT", "/api/posts/{id}", menu, 6);
        upsertApi("api:DELETE:/api/posts/{id}", "删除岗位", "DELETE", "/api/posts/{id}", menu, 7);
        upsertApi("api:POST:/api/posts/batch-delete", "批量删除岗位", "POST", "/api/posts/batch-delete", menu, 8);
        upsertApi("api:PUT:/api/posts/{id}/status", "更新岗位状态", "PUT", "/api/posts/{id}/status", menu, 9);
        upsertApi("api:POST:/api/posts/import", "导入岗位", "POST", "/api/posts/import", menu, 10);
        grantApiToAllRoles("api:GET:/api/posts/options");
    }

    /** 任务执行日志：挂在「系统工具」下 */
    private void ensureJobLogPermissions() {
        Permission tools = permissionRepository.findByCode("menu:system:tools")
                .or(() -> permissionRepository.findByCode("menu:system"))
                .orElse(null);
        Permission menu = ensureMenuPermission("menu:system:job-log", "任务日志", "/system/jobs/logs", tools, 3);
        upsertButton("joblog:delete", "删除", menu, 1);
        upsertButton("joblog:clean", "清空", menu, 2);
        upsertButton("joblog:export", "导出", menu, 3);
        upsertTableButton("joblog:table-view", "详情", menu, 1);
        upsertTableButton("joblog:table-delete", "删除", menu, 2);
        for (String code : List.of("joblog:delete", "joblog:clean", "joblog:export",
                "joblog:table-view", "joblog:table-delete")) {
            permissionRepository.findByCode(code).ifPresent(this::grantToPrivilegedRoles);
        }
        upsertApi("api:GET:/api/logs/job", "任务日志列表", "GET", "/api/logs/job", menu, 1);
        upsertApi("api:GET:/api/logs/job/export", "导出任务日志", "GET", "/api/logs/job/export", menu, 2);
        upsertApi("api:GET:/api/logs/job/{id}", "任务日志详情", "GET", "/api/logs/job/{id}", menu, 3);
        upsertApi("api:DELETE:/api/logs/job/{id}", "删除任务日志", "DELETE", "/api/logs/job/{id}", menu, 4);
        upsertApi("api:POST:/api/logs/job/batch-delete", "批量删除任务日志", "POST", "/api/logs/job/batch-delete", menu, 5);
        upsertApi("api:DELETE:/api/logs/job/clean", "清空任务日志", "DELETE", "/api/logs/job/clean", menu, 6);

        // 定时任务页增加「日志」入口
        Permission jobMenu = permissionRepository.findByCode("menu:system:job").orElse(null);
        if (jobMenu != null) {
            upsertButton("job:logs", "任务日志", jobMenu, 6);
            upsertTableButton("job:table-logs", "日志", jobMenu, 5);
            for (String code : List.of("job:logs", "job:table-logs")) {
                permissionRepository.findByCode(code).ifPresent(this::grantToPrivilegedRoles);
            }
        }
    }

    /** 单位管理：菜单 + CRUD，挂在「组织与账号」下 */
    private void ensureUnitPermissions() {
        Permission menuOrg = permissionRepository.findByCode("menu:system:org").orElse(null);
        Permission menuUser = permissionRepository.findByCode("menu:system:user").orElse(null);
        Permission parent = menuOrg;
        if (parent == null && menuUser != null) {
            parent = menuUser.getParent();
        }
        if (parent == null) {
            parent = permissionRepository.findByCode("menu:system").orElse(null);
        }
        Permission menu = permissionRepository.findByCode("menu:system:unit").orElse(null);
        if (menu == null) {
            int nextSort = (menuUser != null && menuUser.getSort() != null ? menuUser.getSort() : 0) + 1;
            Permission created = new Permission();
            created.setCode("menu:system:unit");
            created.setName("单位管理");
            created.setType(PermissionType.MENU);
            created.setPath("/system/units");
            created.setParent(parent);
            created.setSort(nextSort);
            created.setBuiltIn(true);
            menu = permissionRepository.save(created);
        } else if (parent != null && (menu.getParent() == null || !menu.getParent().getId().equals(parent.getId()))) {
            menu.setParent(parent);
            permissionRepository.save(menu);
        }
        grantToPrivilegedRoles(menu);
        ensureDualCrudButtons(menu, "unit", false, true);
        upsertTableButton("unit:assign", "分配角色", menu, 5);
        permissionRepository.findByCode("unit:assign").ifPresent(this::grantToPrivilegedRoles);

        upsertApi("api:GET:/api/units/tree", "单位树接口", "GET", "/api/units/tree", menu, 1);
        upsertApi("api:GET:/api/units/options", "单位选项", "GET", "/api/units/options", menu, 2);
        upsertApi("api:GET:/api/units/{id}", "单位详情接口", "GET", "/api/units/{id}", menu, 3);
        upsertApi("api:POST:/api/units", "创建单位接口", "POST", "/api/units", menu, 4);
        upsertApi("api:PUT:/api/units/{id}", "更新单位接口", "PUT", "/api/units/{id}", menu, 5);
        upsertApi("api:DELETE:/api/units/{id}", "删除单位接口", "DELETE", "/api/units/{id}", menu, 6);
        upsertApi("api:POST:/api/units/batch-delete", "批量删除单位", "POST", "/api/units/batch-delete", menu, 7);
        upsertApi("api:PUT:/api/units/{id}/status", "更新单位状态", "PUT", "/api/units/{id}/status", menu, 8);
        upsertApi("api:PUT:/api/units/{id}/roles", "分配单位角色", "PUT", "/api/units/{id}/roles", menu, 9);
    }

    /** 公告管理：按钮 + 管理端/用户端接口权限，挂在「内容运营」下 */
    private void ensureNoticePermissions() {
        Permission menuContent = permissionRepository.findByCode("menu:system:content").orElse(null);
        Permission menuSystem = permissionRepository.findByCode("menu:system").orElse(null);
        Permission parent = menuContent != null ? menuContent : menuSystem;
        Permission menu = permissionRepository.findByCode("menu:system:notice").orElse(null);
        if (menu == null) {
            int nextSort = permissionRepository.findAll().stream()
                    .filter(p -> p.getParent() != null && parent != null
                            && p.getParent().getId().equals(parent.getId()))
                    .mapToInt(p -> p.getSort() == null ? 0 : p.getSort())
                    .max()
                    .orElse(0) + 1;
            Permission created = new Permission();
            created.setCode("menu:system:notice");
            created.setName("公告管理");
            created.setType(PermissionType.MENU);
            created.setPath("/system/notices");
            created.setParent(parent);
            created.setSort(nextSort);
            created.setBuiltIn(true);
            menu = permissionRepository.save(created);
        } else if (parent != null && (menu.getParent() == null || !menu.getParent().getId().equals(parent.getId()))) {
            menu.setParent(parent);
            permissionRepository.save(menu);
        }
        grantToPrivilegedRoles(menu);
        ensureDualCrudButtons(menu, "notice", false, false);
        upsertTableButton("notice:table-publish", "下发", menu, 4);
        upsertTableButton("notice:table-revoke", "撤回", menu, 5);
        upsertTableButton("notice:table-readers", "已读明细", menu, 6);
        for (String code : List.of("notice:table-publish", "notice:table-revoke", "notice:table-readers")) {
            permissionRepository.findByCode(code).ifPresent(this::grantToPrivilegedRoles);
        }
        upsertButton("notice:publish", "下发", menu, 5);
        upsertButton("notice:revoke", "撤回", menu, 6);
        permissionRepository.findByCode("notice:publish").ifPresent(this::grantToPrivilegedRoles);
        permissionRepository.findByCode("notice:revoke").ifPresent(this::grantToPrivilegedRoles);

        upsertApi("api:GET:/api/notices", "公告列表接口", "GET", "/api/notices", menu, 1);
        upsertApi("api:GET:/api/notices/{id}", "公告详情接口", "GET", "/api/notices/{id}", menu, 2);
        upsertApi("api:POST:/api/notices", "创建公告接口", "POST", "/api/notices", menu, 3);
        upsertApi("api:PUT:/api/notices/{id}", "更新公告接口", "PUT", "/api/notices/{id}", menu, 4);
        upsertApi("api:DELETE:/api/notices/{id}", "删除公告接口", "DELETE", "/api/notices/{id}", menu, 5);
        upsertApi("api:POST:/api/notices/{id}/publish", "下发公告接口", "POST", "/api/notices/{id}/publish", menu, 6);
        upsertApi("api:POST:/api/notices/{id}/revoke", "撤回公告接口", "POST", "/api/notices/{id}/revoke", menu, 7);
        upsertApi("api:GET:/api/notices/{id}/readers", "公告已读明细", "GET", "/api/notices/{id}/readers", menu, 8);
        upsertApi("api:POST:/api/notices/batch-delete", "批量删除公告", "POST", "/api/notices/batch-delete", menu, 9);
        upsertApi("api:POST:/api/notices/batch-publish", "批量下发公告", "POST", "/api/notices/batch-publish", menu, 10);
        upsertApi("api:POST:/api/notices/batch-revoke", "批量撤回公告", "POST", "/api/notices/batch-revoke", menu, 11);

        Permission menuDashboard = permissionRepository.findByCode("menu:dashboard").orElse(menu);
        upsertApi("api:GET:/api/notices/mine", "我的公告接口", "GET", "/api/notices/mine", menuDashboard, 20);
        upsertApi("api:POST:/api/notices/{id}/read", "标记公告已读", "POST", "/api/notices/{id}/read", menuDashboard, 21);
        grantNoticeInboxToAllRoles();
    }

    /** 我的公告 / 已读回执：全部启用角色可读 */
    private void grantNoticeInboxToAllRoles() {
        for (String code : List.of("api:GET:/api/notices/mine", "api:POST:/api/notices/{id}/read")) {
            Permission permission = permissionRepository.findByCode(code).orElse(null);
            if (permission == null) {
                continue;
            }
            for (Role role : roleRepository.findAll()) {
                Role managed = roleRepository.findByIdWithPermissions(role.getId()).orElse(role);
                Set<Permission> perms = new HashSet<>(managed.getPermissions() == null ? Set.of() : managed.getPermissions());
                if (perms.add(permission)) {
                    managed.setPermissions(perms);
                    roleRepository.save(managed);
                }
            }
        }
    }

    /**
     * 补齐各业务页「接口权限」：挂到对应菜单下，保证权限内容可完整展示与分配。
     * 使用 ensureMenuPermission 保证父菜单一定存在，避免菜单缺失时接口未登记。
     */
    private void ensurePageApiPermissions() {
        Permission system = permissionRepository.findByCode("menu:system").orElse(null);
        Permission org = permissionRepository.findByCode("menu:system:org").orElse(system);
        Permission rbac = permissionRepository.findByCode("menu:system:rbac").orElse(system);

        Permission menuUser = ensureMenuPermission("menu:system:user", "用户管理", "/users", org, 1);
        Permission menuUnit = ensureMenuPermission("menu:system:unit", "单位管理", "/system/units", org, 2);
        Permission menuRole = ensureMenuPermission("menu:system:role", "角色列表", "/system/roles", rbac, 1);
        Permission menuPermission = ensureMenuPermission(
                "menu:system:permission", "角色权限", "/system/permissions", rbac, 2);
        Permission menuPermContent = ensureMenuPermission(
                "menu:system:permission-content", "权限内容", "/system/permissions-content", rbac, 3);
        Permission menuRoute = ensureMenuPermission("menu:system:route", "路由管理", "/system/routes", rbac, 4);
        Permission menuDashboard = ensureMenuPermission("menu:dashboard", "首页", "/dashboard", null, 1);

        // 用户管理
        upsertApi("api:GET:/api/users", "用户列表接口", "GET", "/api/users", menuUser, 1);
        upsertApi("api:GET:/api/users/{id}", "用户详情接口", "GET", "/api/users/{id}", menuUser, 2);
        upsertApi("api:POST:/api/users", "创建用户接口", "POST", "/api/users", menuUser, 3);
        upsertApi("api:PUT:/api/users/{id}", "更新用户接口", "PUT", "/api/users/{id}", menuUser, 4);
        upsertApi("api:DELETE:/api/users/{id}", "删除用户接口", "DELETE", "/api/users/{id}", menuUser, 5);
        upsertApi("api:PATCH:/api/users/{id}/status", "更新用户状态", "PATCH", "/api/users/{id}/status", menuUser, 6);
        upsertApi("api:POST:/api/users/batch-delete", "批量删除用户", "POST", "/api/users/batch-delete", menuUser, 7);
        upsertApi("api:POST:/api/users/import", "导入用户接口", "POST", "/api/users/import", menuUser, 8);
        upsertApi("api:GET:/api/users/export", "导出用户接口", "GET", "/api/users/export", menuUser, 9);

        // 角色列表
        upsertApi("api:GET:/api/roles", "角色列表接口", "GET", "/api/roles", menuRole, 1);
        upsertApi("api:GET:/api/roles/options", "角色选项", "GET", "/api/roles/options", menuRole, 2);
        upsertApi("api:GET:/api/roles/{id}", "角色详情接口", "GET", "/api/roles/{id}", menuRole, 3);
        upsertApi("api:POST:/api/roles", "创建角色接口", "POST", "/api/roles", menuRole, 4);
        upsertApi("api:PUT:/api/roles/{id}", "更新角色接口", "PUT", "/api/roles/{id}", menuRole, 5);
        upsertApi("api:DELETE:/api/roles/{id}", "删除角色接口", "DELETE", "/api/roles/{id}", menuRole, 6);
        upsertApi("api:PUT:/api/roles/{id}/permissions", "分配权限接口", "PUT", "/api/roles/{id}/permissions", menuRole, 7);
        upsertApi("api:PUT:/api/roles/{id}/status", "更新角色状态", "PUT", "/api/roles/{id}/status", menuRole, 8);
        upsertApi("api:POST:/api/roles/batch-delete", "批量删除角色", "POST", "/api/roles/batch-delete", menuRole, 9);

        // 路由管理
        upsertApi("api:GET:/api/routes/tree", "路由树接口", "GET", "/api/routes/tree", menuRoute, 1);
        upsertApi("api:GET:/api/routes/{id}", "路由详情接口", "GET", "/api/routes/{id}", menuRoute, 2);
        upsertApi("api:POST:/api/routes", "创建路由接口", "POST", "/api/routes", menuRoute, 3);
        upsertApi("api:PUT:/api/routes/{id}", "更新路由接口", "PUT", "/api/routes/{id}", menuRoute, 4);
        upsertApi("api:DELETE:/api/routes/{id}", "删除路由接口", "DELETE", "/api/routes/{id}", menuRoute, 5);
        upsertApi("api:POST:/api/routes/batch-delete", "批量删除路由", "POST", "/api/routes/batch-delete", menuRoute, 6);
        upsertApi("api:POST:/api/routes/{id}/generate", "路由代码生成", "POST", "/api/routes/{id}/generate", menuRoute, 7);

        // 权限内容
        upsertApi("api:GET:/api/permissions/tree", "权限树接口", "GET", "/api/permissions/tree", menuPermContent, 1);
        upsertApi("api:POST:/api/permissions", "创建权限接口", "POST", "/api/permissions", menuPermContent, 2);
        upsertApi("api:PUT:/api/permissions/{id}", "更新权限接口", "PUT", "/api/permissions/{id}", menuPermContent, 3);
        upsertApi("api:DELETE:/api/permissions/{id}", "删除权限接口", "DELETE", "/api/permissions/{id}", menuPermContent, 4);
        upsertApi("api:GET:/api/permissions/{id}/groups", "菜单子权限分组", "GET", "/api/permissions/{id}/groups", menuPermContent, 5);

        // 单位：跨页下拉/树（用户、单位页共用）
        upsertApi("api:GET:/api/units/tree", "单位树接口", "GET", "/api/units/tree", menuUnit, 1);
        upsertApi("api:GET:/api/units/options", "单位选项", "GET", "/api/units/options", menuUnit, 2);

        grantToPrivilegedRoles(menuPermission);
        cleanupUnusedPermissionMenuButtons();

        // 工作台及登录后全局接口（所有角色需要）
        upsertApi("api:GET:/api/dashboard/stats", "工作台统计", "GET", "/api/dashboard/stats", menuDashboard, 1);
        upsertApi("api:GET:/api/auth/me", "当前用户", "GET", "/api/auth/me", menuDashboard, 2);
        upsertApi("api:GET:/api/auth/menus", "用户菜单接口", "GET", "/api/auth/menus", menuDashboard, 3);
        upsertApi("api:GET:/api/page-ui", "页面UI配置接口", "GET", "/api/page-ui", menuDashboard, 4);
        upsertApi("api:GET:/api/table-columns", "表格列配置查询", "GET", "/api/table-columns", menuDashboard, 5);
        upsertApi("api:PUT:/api/table-columns", "表格列配置保存", "PUT", "/api/table-columns", menuDashboard, 6);
        upsertApi("api:PUT:/api/auth/me", "更新个人信息", "PUT", "/api/auth/me", menuDashboard, 7);
        upsertApi("api:POST:/api/auth/refresh", "刷新令牌", "POST", "/api/auth/refresh", menuDashboard, 8);
        upsertApi("api:PUT:/api/auth/me/password", "修改密码", "PUT", "/api/auth/me/password", menuDashboard, 9);
        upsertApi("api:POST:/api/auth/me/avatar", "上传头像", "POST", "/api/auth/me/avatar", menuDashboard, 10);
        upsertApi("api:GET:/api/auth/password-rules", "密码规则", "GET", "/api/auth/password-rules", menuDashboard, 11);

        for (String code : List.of(
                "api:GET:/api/dashboard/stats",
                "api:GET:/api/auth/me",
                "api:GET:/api/auth/menus",
                "api:GET:/api/page-ui",
                "api:GET:/api/table-columns",
                "api:PUT:/api/table-columns",
                "api:PUT:/api/auth/me",
                "api:POST:/api/auth/refresh",
                "api:GET:/api/roles/options",
                "api:GET:/api/units/tree",
                "api:GET:/api/units/options",
                "api:GET:/api/dict-data/type/{dictType}",
                "api:GET:/api/dict-types/options"
        )) {
            grantApiToAllRoles(code);
        }
        grantProfileUpdateToAllRoles();
        grantRefreshTokenToAllRoles();
    }

    /** Token 续期接口授权给全部启用角色 */
    private void grantRefreshTokenToAllRoles() {
        Permission permission = permissionRepository.findByCode("api:POST:/api/auth/refresh").orElse(null);
        if (permission == null) {
            return;
        }
        for (Role role : roleRepository.findAll()) {
            Role managed = roleRepository.findByIdWithPermissions(role.getId()).orElse(role);
            Set<Permission> perms = new HashSet<>(managed.getPermissions() == null ? Set.of() : managed.getPermissions());
            if (perms.add(permission)) {
                managed.setPermissions(perms);
                roleRepository.save(managed);
            }
        }
    }

    /** 个人信息相关接口授权给全部启用角色（超管仍由业务层禁止编辑） */
    private void grantProfileUpdateToAllRoles() {
        for (String code : List.of(
                "api:PUT:/api/auth/me",
                "api:PUT:/api/auth/me/password",
                "api:POST:/api/auth/me/avatar",
                "api:GET:/api/auth/password-rules"
        )) {
            grantApiToAllRoles(code);
        }
    }

    /** 创建或更新 API 权限：修正名称/父子/排序，并授权给超管与管理员 */
    private void upsertApi(String code, String name, String method, String path, Permission parent, int sort) {
        Permission permission = permissionRepository.findByCode(code).orElse(null);
        if (permission == null) {
            Permission p = new Permission();
            p.setCode(code);
            p.setName(name);
            p.setType(PermissionType.API);
            p.setMethod(method);
            p.setPath(path);
            p.setParent(parent);
            p.setSort(sort);
            p.setBuiltIn(true);
            permission = permissionRepository.save(p);
        } else {
            permission.setName(name);
            permission.setType(PermissionType.API);
            permission.setMethod(method);
            permission.setPath(path);
            permission.setParent(parent);
            permission.setSort(sort);
            permissionRepository.save(permission);
        }
        grantToPrivilegedRoles(permission);
    }

    /** 角色权限页已改为分配器，移除历史 CRUD/分配按钮避免权限内容展示干扰 */
    private void cleanupUnusedPermissionMenuButtons() {
        for (String code : List.of(
                "permission:create",
                "permission:update",
                "permission:view",
                "permission:delete",
                "permission:assign"
        )) {
            permissionRepository.findByCode(code).ifPresent(permission -> {
                roleRepository.findAll().forEach(role -> {
                    if (role.getPermissions() != null && role.getPermissions().contains(permission)) {
                        Set<Permission> perms = new HashSet<>(role.getPermissions());
                        perms.remove(permission);
                        role.setPermissions(perms);
                        roleRepository.save(role);
                    }
                });
                permissionRepository.delete(permission);
            });
        }
    }

    /** 新增「权限内容」菜单权限，用于管理各菜单下的接口/按钮/表格按钮 */
    private void ensurePermissionContentMenu() {
        String code = "menu:system:permission-content";
        Permission menu = permissionRepository.findByCode(code).orElse(null);
        Permission parent = permissionRepository.findByCode("menu:system:rbac")
                .or(() -> permissionRepository.findByCode("menu:system"))
                .orElse(null);
        if (menu == null) {
            if (parent == null) {
                return;
            }
            int nextSort = permissionRepository.findAll().stream()
                    .mapToInt(Permission::getSort)
                    .max()
                    .orElse(0) + 1;
            Permission created = new Permission();
            created.setCode(code);
            created.setName("权限内容");
            created.setType(PermissionType.MENU);
            created.setPath("/system/permissions-content");
            created.setParent(parent);
            created.setSort(nextSort);
            created.setBuiltIn(true);
            menu = permissionRepository.save(created);
        } else if (parent != null && (menu.getParent() == null || !menu.getParent().getId().equals(parent.getId()))) {
            menu.setParent(parent);
            menu.setName("权限内容");
            permissionRepository.save(menu);
        }
        grantToPrivilegedRoles(menu);
        ensurePermissionContentButtons(menu);
    }

    /** 「权限内容」页：工具栏四件套 + 表格编辑/删除（独立权限码） */
    private void ensurePermissionContentButtons(Permission menu) {
        ensureDualCrudButtons(menu, "permission-content", false, false);
    }

    /**
     * 标准双组件按钮：
     * - BUTTON（工具栏 xnButton）：新增、编辑、查看、删除
     * - TABLE_BUTTON（表格纯文本）：查看、编辑、删除；可选 分配权限 / 添加子级
     */
    private void ensureDualCrudButtons(Permission menu, String prefix, boolean withAssign, boolean withAddChild) {
        // 工具栏四件套
        upsertButton(prefix + ":create", "新增", menu, 1);
        upsertButton(prefix + ":update", "编辑", menu, 2);
        upsertButton(prefix + ":view", "查看", menu, 3);
        upsertButton(prefix + ":delete", "删除", menu, 4);
        // 表格纯文本三件套（独立编码，与工具栏分离）
        upsertTableButton(prefix + ":table-view", "查看", menu, 1);
        upsertTableButton(prefix + ":table-edit", "编辑", menu, 2);
        upsertTableButton(prefix + ":table-delete", "删除", menu, 3);

        for (String code : List.of(
                prefix + ":create", prefix + ":update", prefix + ":view", prefix + ":delete",
                prefix + ":table-view", prefix + ":table-edit", prefix + ":table-delete"
        )) {
            permissionRepository.findByCode(code).ifPresent(this::grantToPrivilegedRoles);
        }

        if (withAssign) {
            upsertTableButton(prefix + ":assign", "分配权限", menu, 4);
            permissionRepository.findByCode(prefix + ":assign").ifPresent(this::grantToPrivilegedRoles);
        }
        if (withAddChild) {
            upsertTableButton(prefix + ":add-child", "添加子级", menu, 4);
            permissionRepository.findByCode(prefix + ":add-child").ifPresent(this::grantToPrivilegedRoles);
        }
    }

    /** 删改查（无新增）+ 表格查看/编辑/删除，用于监控类页面 */
    private void ensureViewUpdateDeleteButtons(Permission menu, String prefix) {
        upsertButton(prefix + ":view", "查看", menu, 1);
        upsertButton(prefix + ":update", "编辑", menu, 2);
        upsertButton(prefix + ":delete", "删除", menu, 3);
        upsertTableButton(prefix + ":table-view", "查看", menu, 1);
        upsertTableButton(prefix + ":table-edit", "编辑", menu, 2);
        upsertTableButton(prefix + ":table-delete", "删除", menu, 3);
        for (String code : List.of(
                prefix + ":view", prefix + ":update", prefix + ":delete",
                prefix + ":table-view", prefix + ":table-edit", prefix + ":table-delete"
        )) {
            permissionRepository.findByCode(code).ifPresent(this::grantToPrivilegedRoles);
        }
    }

    /** 表格列配置接口（分页栏设置） */
    private void ensureTableColumnPermissions() {
        Permission menuDashboard = permissionRepository.findByCode("menu:dashboard").orElse(null);
        if (menuDashboard == null) {
            return;
        }
        saveApiStandalone("api:GET:/api/table-columns", "表格列配置查询", "GET", "/api/table-columns", menuDashboard, 5);
        saveApiStandalone("api:PUT:/api/table-columns", "表格列配置保存", "PUT", "/api/table-columns", menuDashboard, 6);
        permissionRepository.findByCode("api:GET:/api/table-columns").ifPresent(this::grantToPrivilegedRoles);
        permissionRepository.findByCode("api:PUT:/api/table-columns").ifPresent(this::grantToPrivilegedRoles);
    }

    private void grantToPrivilegedRoles(Permission permission) {
        for (String roleCode : List.of("SUPER_ADMIN", "ADMIN")) {
            roleRepository.findByCode(roleCode).ifPresent(role -> {
                boolean has = role.getPermissions().stream()
                        .anyMatch(p -> p.getId().equals(permission.getId()));
                if (!has) {
                    Set<Permission> perms = new HashSet<>(role.getPermissions());
                    perms.add(permission);
                    role.setPermissions(perms);
                    roleRepository.save(role);
                }
            });
        }
    }

    /** 每个菜单标准按钮：工具栏四件套 + 表格查看/编辑/删除（独立权限） */
    private void saveCrudButtons(Map<String, Permission> map, String prefix, Permission parent) {
        saveButton(map, prefix + ":create", "新增", parent, 1);
        saveButton(map, prefix + ":update", "编辑", parent, 2);
        saveButton(map, prefix + ":view", "查看", parent, 3);
        saveButton(map, prefix + ":delete", "删除", parent, 4);
        saveTableButton(map, prefix + ":table-view", "查看", parent, 1);
        saveTableButton(map, prefix + ":table-edit", "编辑", parent, 2);
        saveTableButton(map, prefix + ":table-delete", "删除", parent, 3);
    }

    private Map<String, Permission> initPermissions() {
        Map<String, Permission> map = new LinkedHashMap<>();
        int sort = 0;

        Permission menuDashboard = saveMenu(map, "menu:dashboard", "工作台", "/dashboard", null, ++sort);
        Permission menuSystem = saveMenu(map, "menu:system", "系统管理", null, null, ++sort);
        Permission menuOrg = saveMenu(map, "menu:system:org", "组织与账号", null, menuSystem, ++sort);
        Permission menuUser = saveMenu(map, "menu:system:user", "用户管理", "/users", menuOrg, ++sort);
        Permission menuRbac = saveMenu(map, "menu:system:rbac", "权限与安全", null, menuSystem, ++sort);
        Permission menuRoute = saveMenu(map, "menu:system:route", "路由管理", "/system/routes", menuRbac, ++sort);
        Permission menuRole = saveMenu(map, "menu:system:role", "角色列表", "/system/roles", menuRbac, ++sort);
        Permission menuPermContent = saveMenu(map, "menu:system:permission-content", "权限内容", "/system/permissions-content", menuRbac, ++sort);
        saveMenu(map, "menu:system:permission", "角色权限", "/system/permissions", menuRbac, ++sort);
        saveMenu(map, "menu:system:content", "内容运营", null, menuSystem, ++sort);
        Permission menuPersonal = saveMenu(map, "menu:personal", "个人中心", null, null, ++sort);
        saveMenu(map, "menu:profile", "个人信息", "/profile", menuPersonal, ++sort);

        saveCrudButtons(map, "user", menuUser);

        saveCrudButtons(map, "role", menuRole);
        saveTableButton(map, "role:assign", "分配权限", menuRole, 4);

        // 角色权限页主要用于分配；权限维护在「权限内容」
        saveCrudButtons(map, "permission-content", menuPermContent);
        saveApi(map, "api:GET:/api/permissions/tree", "权限树接口", "GET", "/api/permissions/tree", menuPermContent, 1);
        saveApi(map, "api:POST:/api/permissions", "创建权限接口", "POST", "/api/permissions", menuPermContent, 2);
        saveApi(map, "api:PUT:/api/permissions/{id}", "更新权限接口", "PUT", "/api/permissions/{id}", menuPermContent, 3);
        saveApi(map, "api:DELETE:/api/permissions/{id}", "删除权限接口", "DELETE", "/api/permissions/{id}", menuPermContent, 4);
        saveApi(map, "api:GET:/api/permissions/{id}/groups", "菜单子权限分组", "GET", "/api/permissions/{id}/groups", menuPermContent, 5);

        saveCrudButtons(map, "route", menuRoute);
        saveTableButton(map, "route:add-child", "添加子级", menuRoute, 4);
        saveTableButton(map, "route:generate", "代码生成", menuRoute, 5);

        saveApi(map, "api:GET:/api/dashboard/stats", "工作台统计", "GET", "/api/dashboard/stats", menuDashboard, 1);
        saveApi(map, "api:GET:/api/auth/me", "当前用户", "GET", "/api/auth/me", menuDashboard, 2);
        saveApi(map, "api:PUT:/api/auth/me", "更新个人信息", "PUT", "/api/auth/me", menuDashboard, 7);

        saveApi(map, "api:GET:/api/users", "用户列表接口", "GET", "/api/users", menuUser, 1);
        saveApi(map, "api:GET:/api/users/{id}", "用户详情接口", "GET", "/api/users/{id}", menuUser, 2);
        saveApi(map, "api:POST:/api/users", "创建用户接口", "POST", "/api/users", menuUser, 3);
        saveApi(map, "api:PUT:/api/users/{id}", "更新用户接口", "PUT", "/api/users/{id}", menuUser, 4);
        saveApi(map, "api:DELETE:/api/users/{id}", "删除用户接口", "DELETE", "/api/users/{id}", menuUser, 5);
        saveApi(map, "api:POST:/api/users/batch-delete", "批量删除用户", "POST", "/api/users/batch-delete", menuUser, 7);
        saveApi(map, "api:PATCH:/api/users/{id}/status", "更新用户状态", "PATCH", "/api/users/{id}/status", menuUser, 6);

        saveApi(map, "api:GET:/api/roles", "角色列表接口", "GET", "/api/roles", menuRole, 1);
        saveApi(map, "api:GET:/api/roles/options", "角色选项", "GET", "/api/roles/options", menuRole, 2);
        saveApi(map, "api:GET:/api/roles/{id}", "角色详情接口", "GET", "/api/roles/{id}", menuRole, 3);
        saveApi(map, "api:POST:/api/roles", "创建角色接口", "POST", "/api/roles", menuRole, 4);
        saveApi(map, "api:PUT:/api/roles/{id}", "更新角色接口", "PUT", "/api/roles/{id}", menuRole, 5);
        saveApi(map, "api:DELETE:/api/roles/{id}", "删除角色接口", "DELETE", "/api/roles/{id}", menuRole, 6);
        saveApi(map, "api:POST:/api/roles/batch-delete", "批量删除角色", "POST", "/api/roles/batch-delete", menuRole, 9);
        saveApi(map, "api:PUT:/api/roles/{id}/permissions", "分配权限接口", "PUT", "/api/roles/{id}/permissions", menuRole, 7);
        saveApi(map, "api:PUT:/api/roles/{id}/status", "更新角色状态", "PUT", "/api/roles/{id}/status", menuRole, 8);

        saveApi(map, "api:GET:/api/routes/tree", "路由树接口", "GET", "/api/routes/tree", menuRoute, 1);
        saveApi(map, "api:GET:/api/routes/{id}", "路由详情接口", "GET", "/api/routes/{id}", menuRoute, 2);
        saveApi(map, "api:POST:/api/routes", "创建路由接口", "POST", "/api/routes", menuRoute, 3);
        saveApi(map, "api:PUT:/api/routes/{id}", "更新路由接口", "PUT", "/api/routes/{id}", menuRoute, 4);
        saveApi(map, "api:DELETE:/api/routes/{id}", "删除路由接口", "DELETE", "/api/routes/{id}", menuRoute, 5);
        saveApi(map, "api:POST:/api/routes/batch-delete", "批量删除路由", "POST", "/api/routes/batch-delete", menuRoute, 6);
        saveApi(map, "api:POST:/api/routes/{id}/generate", "路由代码生成", "POST", "/api/routes/{id}/generate", menuRoute, 7);
        saveApi(map, "api:GET:/api/auth/menus", "用户菜单接口", "GET", "/api/auth/menus", menuDashboard, 3);
        saveApi(map, "api:GET:/api/page-ui", "页面UI配置接口", "GET", "/api/page-ui", menuDashboard, 4);
        saveApi(map, "api:GET:/api/table-columns", "表格列配置查询", "GET", "/api/table-columns", menuDashboard, 5);
        saveApi(map, "api:PUT:/api/table-columns", "表格列配置保存", "PUT", "/api/table-columns", menuDashboard, 6);

        return map;
    }

    private Permission saveMenu(Map<String, Permission> map, String code, String name, String path, Permission parent, int sort) {
        Permission p = new Permission();
        p.setCode(code);
        p.setName(name);
        p.setType(PermissionType.MENU);
        p.setPath(path);
        p.setParent(parent);
        p.setSort(sort);
        p.setBuiltIn(true);
        return save(map, code, p);
    }

    private void saveButton(Map<String, Permission> map, String code, String name, Permission parent, int sort) {
        Permission p = new Permission();
        p.setCode(code);
        p.setName(name);
        p.setType(PermissionType.BUTTON);
        p.setParent(parent);
        p.setSort(sort);
        p.setBuiltIn(true);
        applyButtonMeta(p, code);
        save(map, code, p);
    }

    /** 表格操作列按钮，如编辑/删除/分配权限等行内操作（默认无图标） */
    private void saveTableButton(Map<String, Permission> map, String code, String name, Permission parent, int sort) {
        Permission p = new Permission();
        p.setCode(code);
        p.setName(name);
        p.setType(PermissionType.TABLE_BUTTON);
        p.setParent(parent);
        p.setSort(sort);
        p.setBuiltIn(true);
        applyButtonMeta(p, code);
        p.setIcon(null);
        save(map, code, p);
    }

    /** 按 code 后缀约定回填按钮 UI 元数据：action / icon / buttonColor */
    private void applyButtonMeta(Permission p, String code) {
        String suffix = code.contains(":") ? code.substring(code.lastIndexOf(':') + 1) : code;
        switch (suffix) {
            case "create" -> setMeta(p, "add", "Plus", "primary");
            case "add-child" -> setMeta(p, "add-child", null, "primary"); // 表格按钮默认无图标
            case "update" -> setMeta(p, "edit", "Edit", "primary");
            case "view" -> setMeta(p, "view", "View", "primary");
            case "delete" -> setMeta(p, "delete", "Delete", "danger");
            case "table-edit" -> setMeta(p, "edit", null, "primary");
            case "table-delete" -> setMeta(p, "delete", null, "danger");
            case "assign" -> setMeta(p, "assign", null, "primary");
            case "import" -> setMeta(p, "import", "Upload", "success");
            case "publish" -> setMeta(p, "publish", "Promotion", "success");
            case "revoke" -> setMeta(p, "revoke", "RefreshLeft", "warning");
            case "table-publish" -> setMeta(p, "publish", null, "success");
            case "table-revoke" -> setMeta(p, "revoke", null, "warning");
            case "table-readers" -> setMeta(p, "readers", null, "primary");
            case "readers" -> setMeta(p, "readers", null, "primary");
            case "table-data" -> setMeta(p, "data", null, "primary");
            case "table-enable" -> setMeta(p, "enable", null, "success");
            case "table-disable" -> setMeta(p, "disable", null, "warning");
            case "clean" -> setMeta(p, "clean", "Delete", "danger");
            case "export" -> setMeta(p, "export", "Download", "success");
            case "table-view" -> setMeta(p, "view", null, "primary");
            case "send" -> setMeta(p, "send", "Promotion", "success");
            case "table-send" -> setMeta(p, "send", null, "success");
            case "run" -> setMeta(p, "run", "VideoPlay", "success");
            case "table-run" -> setMeta(p, "run", null, "success");
            case "logs" -> setMeta(p, "logs", "Document", "primary");
            case "table-logs" -> setMeta(p, "logs", null, "primary");
            case "restore" -> setMeta(p, "restore", "RefreshLeft", "success");
            case "table-restore" -> setMeta(p, "restore", null, "success");
            case "purge" -> setMeta(p, "purge", "Delete", "danger");
            case "table-purge" -> setMeta(p, "purge", null, "danger");
            case "generate" -> setMeta(p, "generate", null, "success");
            case "refresh" -> setMeta(p, "refresh", "Refresh", "default");
            case "flush" -> setMeta(p, "flush", "Delete", "danger");
            case "table-kick" -> setMeta(p, "kick", null, "danger");
            case "table-offline" -> setMeta(p, "offline", null, "danger");
            case "offline" -> setMeta(p, "offline", "SwitchButton", "danger");
            case "table-unlock" -> setMeta(p, "unlock", null, "primary");
            case "unlock" -> setMeta(p, "unlock", "Unlock", "warning");
            case "kick" -> setMeta(p, "kick", null, "danger");
            case "restart" -> setMeta(p, "restart", "RefreshRight", "warning");
            case "mkdir" -> setMeta(p, "mkdir", "FolderAdd", "primary");
            case "upload" -> setMeta(p, "upload", "Upload", "primary");
            case "table-enter" -> setMeta(p, "enter", null, "primary");
            case "table-preview" -> setMeta(p, "preview", null, "primary");
            case "table-open" -> setMeta(p, "open", null, "primary");
            default -> setMeta(p, suffix, null, "primary");
        }
    }

    private void setMeta(Permission p, String action, String icon, String color) {
        p.setAction(action);
        p.setIcon(icon);
        p.setButtonColor(color);
    }

    private void saveApi(Map<String, Permission> map, String code, String name, String method, String path, Permission parent, int sort) {
        Permission p = new Permission();
        p.setCode(code);
        p.setName(name);
        p.setType(PermissionType.API);
        p.setMethod(method);
        p.setPath(path);
        p.setParent(parent);
        p.setSort(sort);
        p.setBuiltIn(true);
        save(map, code, p);
    }

    private Permission save(Map<String, Permission> map, String code, Permission p) {
        Permission saved = permissionRepository.save(p);
        map.put(code, saved);
        return saved;
    }

    private Map<String, Role> initRoles(Map<String, Permission> permissions) {
        Role superAdmin = createRole("SUPER_ADMIN", "超级管理员", "拥有全部权限，系统兜底角色", true);
        Role admin = createRole("ADMIN", "管理员", "日常管理，含用户/角色/权限", true);
        Role user = createRole("USER", "普通用户", "工作台与只读权限", true);

        superAdmin.setPermissions(new HashSet<>(permissions.values()));
        admin.setPermissions(new HashSet<>(permissions.values()));

        Set<Permission> userPerms = new HashSet<>();
        List<String> userCodes = List.of(
                "menu:dashboard", "menu:system", "menu:system:user",
                "user:view",
                "api:GET:/api/dashboard/stats", "api:GET:/api/auth/me", "api:GET:/api/users"
        );
        userCodes.forEach(code -> {
            if (permissions.containsKey(code)) {
                userPerms.add(permissions.get(code));
            }
        });
        user.setPermissions(userPerms);

        roleRepository.saveAll(List.of(superAdmin, admin, user));
        return Map.of("SUPER_ADMIN", superAdmin, "ADMIN", admin, "USER", user);
    }

    private Role createRole(String code, String name, String desc, boolean builtIn) {
        Role role = new Role();
        role.setCode(code);
        role.setName(name);
        role.setDescription(desc);
        role.setStatus(1);
        role.setBuiltIn(builtIn);
        if ("SUPER_ADMIN".equals(code) || "ADMIN".equals(code)) {
            role.setDataScope(DataScope.ALL);
        } else {
            role.setDataScope(DataScope.UNIT_AND_CHILDREN);
        }
        return role;
    }

    /** 内置角色展示名：SUPER_ADMIN=超级管理员，ADMIN=管理员 */
    private void ensureBuiltinRoles() {
        roleRepository.findByCode("SUPER_ADMIN").ifPresent(role -> {
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
        roleRepository.findByCode("ADMIN").ifPresent(role -> {
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

    /**
     * 种子账号（幂等校正用户名大小写 / 密码 / 角色）：
     * SuperAdmin / SuperAdmin → 超级管理员；admin / admin → 管理员。
     */
    private void ensureSeedAccounts() {
        Role superAdmin = roleRepository.findByCode("SUPER_ADMIN").orElse(null);
        Role adminRole = roleRepository.findByCode("ADMIN").orElse(null);

        // 旧超管用户名：SUPER_ADMIN / superadmin 等 → SuperAdmin
        normalizeSeedUser(
                List.of("SuperAdmin", "SUPER_ADMIN", "superadmin", "superAdmin", "SUPERADMIN"),
                "SuperAdmin",
                "SuperAdmin",
                "超级管理员",
                "superadmin@smartadmin.com",
                "13800000000",
                "ADMIN",
                superAdmin
        );

        // 若仍无超管账号，且旧 admin 挂着 SUPER_ADMIN 角色，则升迁并改名
        if (superAdmin != null && userRepository.findByUsernameIgnoreCase("SuperAdmin").isEmpty()) {
            userRepository.findByUsernameWithRoles("admin").ifPresent(legacy -> {
                boolean wasSuper = legacy.getRoles() != null && legacy.getRoles().stream()
                        .anyMatch(r -> "SUPER_ADMIN".equals(r.getCode()));
                if (wasSuper) {
                    legacy.setUsername("SuperAdmin");
                    legacy.setNickname("超级管理员");
                    legacy.setPassword(passwordEncoder.encode("SuperAdmin"));
                    legacy.setEmail("superadmin@smartadmin.com");
                    legacy.setPhone("13800000000");
                    legacy.setStatus(1);
                    legacy.setRoles(new HashSet<>(Set.of(superAdmin)));
                    legacy.setRole("ADMIN");
                    userRepository.save(legacy);
                }
            });
        }

        // 旧 sysadmin → admin（管理员）；若 admin 已存在则停用 sysadmin
        userRepository.findByUsernameIgnoreCase("sysadmin").ifPresent(legacy -> {
            if (userRepository.findByUsernameIgnoreCase("admin").isEmpty() && adminRole != null) {
                legacy.setUsername("admin");
                legacy.setNickname("管理员");
                legacy.setPassword(passwordEncoder.encode("admin"));
                legacy.setEmail("admin@smartadmin.com");
                legacy.setPhone("13800000001");
                legacy.setStatus(1);
                legacy.setRoles(new HashSet<>(Set.of(adminRole)));
                legacy.setRole("ADMIN");
                userRepository.save(legacy);
            } else {
                legacy.setRoles(new HashSet<>());
                legacy.setRole("USER");
                legacy.setStatus(0);
                userRepository.save(legacy);
            }
        });

        normalizeSeedUser(
                List.of("admin", "Admin", "ADMIN"),
                "admin",
                "admin",
                "管理员",
                "admin@smartadmin.com",
                "13800000001",
                "ADMIN",
                adminRole
        );
    }

    /** 找到任一别名用户并校正为标准用户名/密码/角色；不存在则新建 */
    private void normalizeSeedUser(List<String> aliases, String username, String rawPassword,
                                   String nickname, String email, String phone,
                                   String legacyRole, Role role) {
        if (role == null) {
            return;
        }
        User user = null;
        for (String alias : aliases) {
            user = userRepository.findByUsernameIgnoreCase(alias).orElse(null);
            if (user != null) {
                break;
            }
        }
        if (user == null) {
            ensureUser(username, rawPassword, nickname, email, phone, legacyRole, role);
            user = userRepository.findByUsernameIgnoreCase(username).orElse(null);
        }
        if (user == null) {
            return;
        }
        // 带角色重载，避免懒加载空集
        user = userRepository.findByIdWithRoles(user.getId()).orElse(user);

        boolean dirty = false;
        if (!username.equals(user.getUsername())) {
            // 大小写不敏感库：先改成临时名再改回，确保落库大小写正确
            String tmp = username + "__tmp_seed__";
            user.setUsername(tmp);
            userRepository.saveAndFlush(user);
            user.setUsername(username);
            dirty = true;
        }
        if (!nickname.equals(user.getNickname())) {
            user.setNickname(nickname);
            dirty = true;
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            user.setStatus(1);
            dirty = true;
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            user.setPassword(passwordEncoder.encode(rawPassword));
            dirty = true;
        }
        Set<Role> expected = new HashSet<>(Set.of(role));
        Set<String> currentCodes = user.getRoles() == null ? Set.of()
                : user.getRoles().stream().map(Role::getCode).collect(java.util.stream.Collectors.toSet());
        if (!currentCodes.equals(Set.of(role.getCode()))) {
            user.setRoles(expected);
            user.setRole("SUPER_ADMIN".equals(role.getCode()) ? "ADMIN" : role.getCode());
            dirty = true;
        }
        if (dirty) {
            userRepository.save(user);
        }
    }

    private void ensureUser(String username, String rawPassword, String nickname,
                            String email, String phone, String legacyRole, Role role) {
        if (role == null || userRepository.existsByUsernameIgnoreCase(username)) {
            return;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setNickname(nickname);
        user.setEmail(email);
        user.setPhone(phone);
        user.setStatus(1);
        user.setRole(legacyRole);
        user.setRoles(new HashSet<>(Set.of(role)));
        userRepository.save(user);
    }

    private void migrateExistingUsers() {
        Role superAdmin = roleRepository.findByCode("SUPER_ADMIN").orElse(null);
        Role userRole = roleRepository.findByCode("USER").orElse(null);
        if (superAdmin == null) {
            return;
        }

        userRepository.findAll().forEach(user -> {
            if (user.getRoles() == null || user.getRoles().isEmpty()) {
                Set<Role> roles = new HashSet<>();
                if ("SuperAdmin".equalsIgnoreCase(user.getUsername())) {
                    roles.add(superAdmin);
                } else if ("ADMIN".equals(user.getRole()) || "admin".equals(user.getUsername())) {
                    Role adminRole = roleRepository.findByCode("ADMIN").orElse(superAdmin);
                    roles.add(adminRole);
                } else {
                    roles.add(userRole != null ? userRole : superAdmin);
                }
                user.setRoles(roles);
                userRepository.save(user);
            }
        });
    }

    /** 为已有数据库补全路由管理相关权限 */
    private void ensureRoutePermissions() {
        if (permissionRepository.findByCode("menu:system:route").isPresent()) {
            return;
        }
        Permission menuSystem = permissionRepository.findByCode("menu:system").orElse(null);
        if (menuSystem == null) {
            return;
        }
        int nextSort = permissionRepository.findAll().stream()
                .mapToInt(Permission::getSort)
                .max()
                .orElse(0) + 1;

        Permission menuRoute = new Permission();
        menuRoute.setCode("menu:system:route");
        menuRoute.setName("路由管理");
        menuRoute.setType(PermissionType.MENU);
        menuRoute.setPath("/system/routes");
        menuRoute.setParent(menuSystem);
        menuRoute.setSort(nextSort);
        menuRoute.setBuiltIn(true);
        menuRoute = permissionRepository.save(menuRoute);

        saveCrudButtonsStandalone("route", menuRoute);

        saveApiStandalone("api:GET:/api/routes/tree", "路由树接口", "GET", "/api/routes/tree", menuRoute, 1);
        saveApiStandalone("api:GET:/api/routes/{id}", "路由详情接口", "GET", "/api/routes/{id}", menuRoute, 2);
        saveApiStandalone("api:POST:/api/routes", "创建路由接口", "POST", "/api/routes", menuRoute, 3);
        saveApiStandalone("api:PUT:/api/routes/{id}", "更新路由接口", "PUT", "/api/routes/{id}", menuRoute, 4);
        saveApiStandalone("api:DELETE:/api/routes/{id}", "删除路由接口", "DELETE", "/api/routes/{id}", menuRoute, 5);
        saveApiStandalone("api:POST:/api/routes/batch-delete", "批量删除路由", "POST", "/api/routes/batch-delete", menuRoute, 6);
        saveApiStandalone("api:POST:/api/routes/{id}/generate", "路由代码生成", "POST", "/api/routes/{id}/generate", menuRoute, 7);
        saveTableButtonStandalone("route:generate", "代码生成", menuRoute, 5);

        Permission menuDashboard = permissionRepository.findByCode("menu:dashboard").orElse(null);
        if (menuDashboard != null && permissionRepository.findByCode("api:GET:/api/auth/menus").isEmpty()) {
            saveApiStandalone("api:GET:/api/auth/menus", "用户菜单接口", "GET", "/api/auth/menus", menuDashboard, 3);
        }

        Role superAdmin = roleRepository.findByCode("SUPER_ADMIN").orElse(null);
        Role admin = roleRepository.findByCode("ADMIN").orElse(null);
        if (superAdmin != null) {
            Set<Permission> perms = new HashSet<>(superAdmin.getPermissions());
            permissionRepository.findAll().forEach(perms::add);
            superAdmin.setPermissions(perms);
            roleRepository.save(superAdmin);
        }
        if (admin != null) {
            Set<Permission> perms = new HashSet<>(admin.getPermissions());
            permissionRepository.findByCode("menu:system:route").ifPresent(perms::add);
            permissionRepository.findAll().stream()
                    .filter(p -> p.getCode() != null && p.getCode().startsWith("route:"))
                    .forEach(perms::add);
            permissionRepository.findByCode("api:GET:/api/routes/tree").ifPresent(perms::add);
            permissionRepository.findByCode("api:GET:/api/routes/{id}").ifPresent(perms::add);
            permissionRepository.findByCode("api:POST:/api/routes").ifPresent(perms::add);
            permissionRepository.findByCode("api:PUT:/api/routes/{id}").ifPresent(perms::add);
            permissionRepository.findByCode("api:DELETE:/api/routes/{id}").ifPresent(perms::add);
            permissionRepository.findByCode("api:POST:/api/routes/batch-delete").ifPresent(perms::add);
            permissionRepository.findByCode("api:GET:/api/auth/menus").ifPresent(perms::add);
            admin.setPermissions(perms);
            roleRepository.save(admin);
        }
    }

    /** 权限配置页改为角色权限分配，同步菜单名称 */
    private void ensurePermissionMenuRenamed() {
        permissionRepository.findByCode("menu:system:permission").ifPresent(menu -> {
            if (!"角色权限".equals(menu.getName())) {
                menu.setName("角色权限");
                permissionRepository.save(menu);
            }
        });
    }

    /** 统一各菜单：工具栏四件套 + 表格编辑/删除；角色加分配权限，路由加添加子级 */
    private void ensureStandardButtonPermissions() {
        ensureMenuCrudButtons("menu:system:user", "user", false, false);
        Permission menuUserForImport = permissionRepository.findByCode("menu:system:user").orElse(null);
        if (menuUserForImport != null) {
            upsertButton("user:import", "导入", menuUserForImport, 5);
            upsertButton("user:export", "导出", menuUserForImport, 6);
            // 能力型按钮：出现在「角色权限 → 用户管理 → 按钮权限」，不进用户页工具栏
            upsertCapabilityButton("user:sensitive:view", "查看敏感信息", menuUserForImport, 7);
            permissionRepository.findByCode("user:import").ifPresent(this::grantToPrivilegedRoles);
            permissionRepository.findByCode("user:export").ifPresent(this::grantToPrivilegedRoles);
            permissionRepository.findByCode("user:sensitive:view").ifPresent(this::grantToPrivilegedRoles);
        }
        ensureMenuCrudButtons("menu:system:role", "role", true, false);
        ensureMenuCrudButtons("menu:system:route", "route", false, true);
    }

    /** 路由管理：代码生成操作列按钮 + 接口权限 */
    private void ensureRouteCodegenPermissions() {
        Permission menuRoute = permissionRepository.findByCode("menu:system:route").orElse(null);
        if (menuRoute == null) {
            return;
        }
        upsertTableButton("route:generate", "代码生成", menuRoute, 5);
        permissionRepository.findByCode("route:generate").ifPresent(this::grantToPrivilegedRoles);
        upsertApi("api:POST:/api/routes/{id}/generate", "路由代码生成", "POST", "/api/routes/{id}/generate", menuRoute, 7);
    }

    private void ensureMenuCrudButtons(String menuCode, String prefix, boolean withAssign, boolean withAddChild) {
        Permission menu = permissionRepository.findByCode(menuCode).orElse(null);
        if (menu == null) {
            return;
        }
        migrateListButtonToView(prefix);
        ensureDualCrudButtons(menu, prefix, withAssign, withAddChild);
    }

    private void migrateListButtonToView(String prefix) {
        String listCode = prefix + ":list";
        String viewCode = prefix + ":view";
        Permission listPerm = permissionRepository.findByCode(listCode).orElse(null);
        if (listPerm == null) {
            return;
        }
        Permission viewPerm = permissionRepository.findByCode(viewCode).orElse(null);
        if (viewPerm != null) {
            roleRepository.findAll().forEach(role -> {
                if (role.getPermissions().contains(listPerm)) {
                    Set<Permission> perms = new HashSet<>(role.getPermissions());
                    perms.add(viewPerm);
                    perms.remove(listPerm);
                    role.setPermissions(perms);
                    roleRepository.save(role);
                }
            });
            permissionRepository.delete(listPerm);
        } else {
            listPerm.setCode(viewCode);
            listPerm.setName("查看");
            listPerm.setSort(3);
            permissionRepository.save(listPerm);
        }
    }

    private void upsertButton(String code, String name, Permission parent, int sort) {
        Permission permission = permissionRepository.findByCode(code).orElse(null);
        if (permission == null) {
            saveButtonStandalone(code, name, parent, sort);
            return;
        }
        permission.setName(name);
        permission.setSort(sort);
        permission.setParent(parent);
        permission.setType(PermissionType.BUTTON);
        applyButtonMeta(permission, code);
        permissionRepository.save(permission);
    }

    /** 仅角色分配用的能力权限：BUTTON 类型便于勾选，action=capability 不进工具栏 */
    private void upsertCapabilityButton(String code, String name, Permission parent, int sort) {
        Permission permission = permissionRepository.findByCode(code).orElse(null);
        if (permission == null) {
            Permission p = new Permission();
            p.setCode(code);
            p.setName(name);
            p.setType(PermissionType.BUTTON);
            p.setParent(parent);
            p.setSort(sort);
            p.setBuiltIn(true);
            p.setAction("capability");
            p.setIcon(null);
            p.setButtonColor("primary");
            p.setMethod(null);
            p.setPath(null);
            permissionRepository.save(p);
            return;
        }
        permission.setName(name);
        permission.setSort(sort);
        permission.setParent(parent);
        permission.setType(PermissionType.BUTTON);
        permission.setAction("capability");
        permission.setIcon(null);
        permission.setButtonColor("primary");
        permission.setMethod(null);
        permission.setPath(null);
        permissionRepository.save(permission);
    }

    private void saveCrudButtonsStandalone(String prefix, Permission parent) {
        ensureDualCrudButtons(parent, prefix, false, "route".equals(prefix));
    }

    private void saveButtonStandalone(String code, String name, Permission parent, int sort) {
        if (permissionRepository.findByCode(code).isPresent()) {
            return;
        }
        Permission p = new Permission();
        p.setCode(code);
        p.setName(name);
        p.setType(PermissionType.BUTTON);
        p.setParent(parent);
        p.setSort(sort);
        p.setBuiltIn(true);
        applyButtonMeta(p, code);
        permissionRepository.save(p);
    }

    private void upsertTableButton(String code, String name, Permission parent, int sort) {
        Permission permission = permissionRepository.findByCode(code).orElse(null);
        if (permission == null) {
            saveTableButtonStandalone(code, name, parent, sort);
            return;
        }
        permission.setName(name);
        permission.setSort(sort);
        permission.setParent(parent);
        permission.setType(PermissionType.TABLE_BUTTON);
        applyButtonMeta(permission, code);
        // 表格按钮默认不带图标
        permission.setIcon(null);
        permissionRepository.save(permission);
    }

    private void saveTableButtonStandalone(String code, String name, Permission parent, int sort) {
        if (permissionRepository.findByCode(code).isPresent()) {
            return;
        }
        Permission p = new Permission();
        p.setCode(code);
        p.setName(name);
        p.setType(PermissionType.TABLE_BUTTON);
        p.setParent(parent);
        p.setSort(sort);
        p.setBuiltIn(true);
        applyButtonMeta(p, code);
        p.setIcon(null);
        permissionRepository.save(p);
    }

    private void saveApiStandalone(String code, String name, String method, String path, Permission parent, int sort) {
        if (permissionRepository.findByCode(code).isPresent()) {
            return;
        }
        Permission p = new Permission();
        p.setCode(code);
        p.setName(name);
        p.setType(PermissionType.API);
        p.setMethod(method);
        p.setPath(path);
        p.setParent(parent);
        p.setSort(sort);
        p.setBuiltIn(true);
        permissionRepository.save(p);
    }
}
