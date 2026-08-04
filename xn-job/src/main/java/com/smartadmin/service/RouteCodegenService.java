package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.RouteCodegenRequest;
import com.smartadmin.dto.RouteCodegenVO;
import com.smartadmin.entity.Permission;
import com.smartadmin.entity.PermissionType;
import com.smartadmin.entity.Role;
import com.smartadmin.entity.RouteType;
import com.smartadmin.entity.SysPageUiConfig;
import com.smartadmin.entity.SysRoute;
import com.smartadmin.repository.PermissionRepository;
import com.smartadmin.repository.RoleRepository;
import com.smartadmin.repository.SysPageUiConfigRepository;
import com.smartadmin.repository.SysRouteRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RouteCodegenService {

    private static final String JAVA_BASE =
            "xn-admin-cloud/xn-system/src/main/java/com/smartadmin/";
    private static final String VUE_BASE = "xn-admin-vue3-ts/src/";

    private final SysRouteRepository routeRepository;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final SysPageUiConfigRepository pageUiConfigRepository;
    private final RbacService rbacService;

    @Transactional
    public RouteCodegenVO generate(Long routeId, RouteCodegenRequest request) {
        rbacService.checkPermission("route:generate");

        SysRoute route =
                routeRepository.findById(routeId).orElseThrow(() -> new BusinessException("路由不存在"));
        if (route.getType() != RouteType.MENU) {
            throw new BusinessException("仅菜单类型可代码生成");
        }
        if (!StringUtils.hasText(route.getPath()) || !StringUtils.hasText(route.getViewPath())) {
            throw new BusinessException("菜单缺少访问路径或视图目录");
        }

        String prefix = normalizePrefix(request.getModulePrefix());
        String apiBase = normalizeApiBase(request.getApiBasePath());
        boolean persistPerms =
                request.getPersistPermissions() == null || request.getPersistPermissions();
        boolean genPageUi = request.getGeneratePageUi() == null || request.getGeneratePageUi();

        Permission menuPerm = resolveMenuPermission(route);

        List<PermSpec> specs = buildPermissionSpecs(prefix, apiBase, route.getTitle());
        List<String> codes = specs.stream().map(PermSpec::code).toList();

        int persisted = 0;
        StringBuilder sql = new StringBuilder();
        sql.append("-- 路由代码生成：")
                .append(route.getTitle())
                .append(" (")
                .append(route.getPath())
                .append(")\n");
        sql.append("-- 模板=CRUD 前缀=").append(prefix).append(" API=").append(apiBase).append("\n\n");

        for (PermSpec spec : specs) {
            sql.append(toInsertSql(spec, menuPerm));
            if (persistPerms) {
                if (upsertPermission(spec, menuPerm)) {
                    persisted++;
                }
            }
        }

        boolean pageUiPersisted = false;
        if (genPageUi) {
            String searchJson = defaultSearchConfig(route.getTitle());
            sql.append("\n").append(toPageUiSql(route.getPath(), searchJson));
            if (persistPerms) {
                pageUiPersisted = upsertPageUi(route.getPath(), searchJson);
            }
        }

        String schemaSql = schemaSql(toTableName(prefix), route.getTitle());
        sql.append("\n").append(schemaSql);

        List<RouteCodegenVO.GeneratedFile> files = buildFiles(route, prefix, apiBase);

        RouteCodegenVO vo = new RouteCodegenVO();
        vo.setRouteId(route.getId());
        vo.setRoutePath(route.getPath());
        vo.setViewPath(route.getViewPath());
        vo.setModulePrefix(prefix);
        vo.setApiBasePath(apiBase);
        vo.setTemplate("CRUD");
        vo.setPermissionCodes(new ArrayList<>(codes));
        vo.setPersistedPermissionCount(persisted);
        vo.setPageUiPersisted(pageUiPersisted);
        vo.setSql(sql.toString());
        vo.setFiles(files);
        vo.setZipBase64(buildZipBase64(files, sql.toString(), prefix));
        return vo;
    }

    private Permission resolveMenuPermission(SysRoute route) {
        if (!StringUtils.hasText(route.getPermission())) {
            throw new BusinessException("路由尚未同步菜单权限，请先保存路由");
        }
        return permissionRepository
                .findByCode(route.getPermission())
                .orElseThrow(() -> new BusinessException("菜单权限不存在: " + route.getPermission()));
    }

    private List<PermSpec> buildPermissionSpecs(String prefix, String apiBase, String title) {
        List<PermSpec> list = new ArrayList<>();
        list.add(button(prefix + ":create", "新增", 1));
        list.add(button(prefix + ":update", "编辑", 2));
        list.add(button(prefix + ":view", "查看", 3));
        list.add(button(prefix + ":delete", "删除", 4));
        list.add(tableButton(prefix + ":table-edit", "编辑", 1));
        list.add(tableButton(prefix + ":table-delete", "删除", 2));

        list.add(api("api:GET:" + apiBase, title + "列表接口", "GET", apiBase, 1));
        list.add(api("api:GET:" + apiBase + "/{id}", title + "详情接口", "GET", apiBase + "/{id}", 2));
        list.add(api("api:POST:" + apiBase, "创建" + title + "接口", "POST", apiBase, 3));
        list.add(
                api(
                        "api:PUT:" + apiBase + "/{id}",
                        "更新" + title + "接口",
                        "PUT",
                        apiBase + "/{id}",
                        4));
        list.add(
                api(
                        "api:DELETE:" + apiBase + "/{id}",
                        "删除" + title + "接口",
                        "DELETE",
                        apiBase + "/{id}",
                        5));
        list.add(
                api(
                        "api:POST:" + apiBase + "/batch-delete",
                        "批量删除" + title,
                        "POST",
                        apiBase + "/batch-delete",
                        6));
        return list;
    }

    private boolean upsertPermission(PermSpec spec, Permission parent) {
        Permission existing = permissionRepository.findByCode(spec.code()).orElse(null);
        boolean created = existing == null;
        Permission p = existing != null ? existing : new Permission();
        if (created) {
            p.setCode(spec.code());
            p.setBuiltIn(false);
        }
        p.setName(spec.name());
        p.setType(spec.type());
        p.setParent(parent);
        p.setSort(spec.sort());
        p.setMethod(spec.method());
        p.setPath(spec.path());
        applyButtonMeta(p, spec.code());
        if (spec.type() == PermissionType.TABLE_BUTTON) {
            p.setIcon(null);
        }
        if (spec.type() == PermissionType.API) {
            p.setAction(null);
            p.setIcon(null);
            p.setButtonColor(null);
        }
        p = permissionRepository.save(p);
        grantToPrivilegedRoles(p);
        return created;
    }

    private boolean upsertPageUi(String routePath, String searchJson) {
        SysPageUiConfig config = pageUiConfigRepository.findByRoutePath(routePath).orElse(null);
        if (config == null) {
            config = new SysPageUiConfig();
            config.setRoutePath(routePath);
            config.setBuiltIn(false);
            config.setSearchConfig(searchJson);
            pageUiConfigRepository.save(config);
            return true;
        }
        if (!StringUtils.hasText(config.getSearchConfig())) {
            config.setSearchConfig(searchJson);
            pageUiConfigRepository.save(config);
            return true;
        }
        return false;
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
                                Set<Permission> perms =
                                        new HashSet<>(
                                                managed.getPermissions() == null
                                                        ? Set.of()
                                                        : managed.getPermissions());
                                if (perms.add(permission)) {
                                    managed.setPermissions(perms);
                                    roleRepository.save(managed);
                                }
                            });
        }
    }

    private List<RouteCodegenVO.GeneratedFile> buildFiles(
            SysRoute route, String prefix, String apiBase) {
        List<RouteCodegenVO.GeneratedFile> files = new ArrayList<>();
        String viewPath = route.getViewPath();
        String title = route.getTitle();
        String routePath = route.getPath();
        String pascal = toPascal(prefix);
        String camel = toCamel(pascal);
        String table = toTableName(prefix);
        String apiModule = prefix;
        String apiUrl = apiBase.startsWith("/api/") ? apiBase.substring(4) : apiBase;

        files.add(
                RouteCodegenVO.GeneratedFile.of(
                        JAVA_BASE + "entity/" + pascal + ".java",
                        entityJava(pascal, table, title)));
        files.add(
                RouteCodegenVO.GeneratedFile.of(
                        JAVA_BASE + "repository/" + pascal + "Repository.java",
                        repositoryJava(pascal)));
        files.add(
                RouteCodegenVO.GeneratedFile.of(
                        JAVA_BASE + "dto/" + pascal + "VO.java", voJava(pascal)));
        files.add(
                RouteCodegenVO.GeneratedFile.of(
                        JAVA_BASE + "dto/" + pascal + "Request.java", requestJava(pascal, title)));
        files.add(
                RouteCodegenVO.GeneratedFile.of(
                        JAVA_BASE + "service/" + pascal + "Service.java",
                        serviceJava(pascal, camel, title, prefix)));
        files.add(
                RouteCodegenVO.GeneratedFile.of(
                        JAVA_BASE + "controller/" + pascal + "Controller.java",
                        controllerJava(pascal, camel, apiBase, title)));
        files.add(
                RouteCodegenVO.GeneratedFile.of(
                        VUE_BASE + "views/" + viewPath + "/index.vue",
                        crudIndexVue(title, routePath, apiModule, prefix)));
        files.add(
                RouteCodegenVO.GeneratedFile.of(
                        VUE_BASE + "views/" + viewPath + "/save.vue",
                        crudSaveVue(title, apiModule, pascal)));
        files.add(
                RouteCodegenVO.GeneratedFile.of(
                        VUE_BASE + "api/" + apiModule + ".ts", apiTs(apiModule, apiUrl)));
        files.add(
                RouteCodegenVO.GeneratedFile.of(
                        "README.md",
                        readmeCrud(title, prefix, pascal, table, apiBase, viewPath, routePath)));
        files.add(
                RouteCodegenVO.GeneratedFile.of(
                        "sql/" + prefix + "-schema.sql", schemaSql(table, title)));
        return files;
    }

    private String buildZipBase64(
            List<RouteCodegenVO.GeneratedFile> files, String permissionSql, String prefix) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                for (RouteCodegenVO.GeneratedFile file : files) {
                    zos.putNextEntry(new ZipEntry(file.getPath()));
                    zos.write(file.getContent().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
                zos.putNextEntry(new ZipEntry("sql/" + prefix + "-permissions.sql"));
                zos.write(permissionSql.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new BusinessException("打包生成文件失败");
        }
    }

    // ---------- naming / meta ----------

    static String normalizePrefix(String raw) {
        String v =
                raw.trim()
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9_-]+", "-")
                        .replaceAll("^-+|-+$", "");
        if (!StringUtils.hasText(v)) {
            throw new BusinessException("模块前缀无效");
        }
        return v;
    }

    static String normalizeApiBase(String raw) {
        String v = raw.trim().replace('\\', '/').replaceAll("/+", "/");
        if (v.endsWith("/") && v.length() > 1) {
            v = v.substring(0, v.length() - 1);
        }
        if (!v.startsWith("/")) {
            v = "/" + v;
        }
        if (!v.startsWith("/api/")) {
            v = "/api" + (v.startsWith("/") ? v : "/" + v);
        }
        return v;
    }

    public static String defaultPrefixFromPath(String path) {
        if (!StringUtils.hasText(path)) {
            return "module";
        }
        String cleaned = path.startsWith("/") ? path.substring(1) : path;
        String[] parts = cleaned.split("/");
        String last = parts[parts.length - 1];
        return normalizePrefix(last);
    }

    public static String defaultApiFromPrefix(String prefix) {
        return "/api/" + prefix;
    }

    static String toPascal(String kebab) {
        StringBuilder sb = new StringBuilder();
        for (String part : kebab.split("[-_]")) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    static String toCamel(String pascal) {
        if (!StringUtils.hasText(pascal)) return pascal;
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    static String toTableName(String prefix) {
        return "biz_" + prefix.replace('-', '_');
    }

    private void applyButtonMeta(Permission p, String code) {
        String suffix = code.contains(":") ? code.substring(code.lastIndexOf(':') + 1) : code;
        switch (suffix) {
            case "create" -> setMeta(p, "add", "Plus", "primary");
            case "update" -> setMeta(p, "edit", "Edit", "primary");
            case "view" -> setMeta(p, "view", "View", "primary");
            case "delete" -> setMeta(p, "delete", "Delete", "danger");
            case "table-edit" -> setMeta(p, "edit", null, "primary");
            case "table-delete" -> setMeta(p, "delete", null, "danger");
            default -> setMeta(p, suffix, null, "primary");
        }
    }

    private void setMeta(Permission p, String action, String icon, String color) {
        p.setAction(action);
        p.setIcon(icon);
        p.setButtonColor(color);
    }

    // ---------- SQL ----------

    private String toInsertSql(PermSpec spec, Permission parent) {
        String parentIdExpr =
                parent.getId() != null
                        ? String.valueOf(parent.getId())
                        : "(SELECT id FROM sys_permission WHERE code = '"
                                + escapeSql(parent.getCode())
                                + "' LIMIT 1)";
        return String.format(
                """
                INSERT INTO sys_permission (code, name, type, parent_id, path, method, action, icon, button_color, sort, built_in)
                SELECT '%s', '%s', '%s', %s, %s, %s, %s, %s, %s, %d, 0
                WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = '%s');

                """,
                escapeSql(spec.code()),
                escapeSql(spec.name()),
                spec.type().name(),
                parentIdExpr,
                sqlNullable(spec.path()),
                sqlNullable(spec.method()),
                sqlNullable(actionOf(spec)),
                sqlNullable(iconOf(spec)),
                sqlNullable(colorOf(spec)),
                spec.sort(),
                escapeSql(spec.code()));
    }

    private String toPageUiSql(String routePath, String searchJson) {
        return String.format(
                """
                INSERT INTO sys_page_ui_config (route_path, search_config, built_in)
                SELECT '%s', '%s', 0
                WHERE NOT EXISTS (SELECT 1 FROM sys_page_ui_config WHERE route_path = '%s');

                """,
                escapeSql(routePath), escapeSql(searchJson), escapeSql(routePath));
    }

    private String defaultSearchConfig(String title) {
        return "[{\"label\":\"综合查询\",\"prop\":\"FuzzyWord\",\"type\":\"input\",\"placeholder\":\"搜索"
                + title
                + "\"}]";
    }

    private String schemaSql(String table, String title) {
        return """
                -- %s 业务表（开发环境也可依赖 JPA ddl-auto:update 由 Entity 自动建表）
                CREATE TABLE IF NOT EXISTS `%s` (
                  `id` BIGINT NOT NULL AUTO_INCREMENT,
                  `code` VARCHAR(50) NOT NULL,
                  `name` VARCHAR(50) NOT NULL,
                  `sort` INT NOT NULL DEFAULT 0,
                  `status` INT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
                  `remark` VARCHAR(200) DEFAULT NULL,
                  `created_at` DATETIME(6) DEFAULT NULL,
                  `updated_at` DATETIME(6) DEFAULT NULL,
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_%s_code` (`code`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

                """
                .formatted(title, table, table);
    }

    private String actionOf(PermSpec spec) {
        if (spec.type() == PermissionType.API) return null;
        String suffix = spec.code().substring(spec.code().lastIndexOf(':') + 1);
        return switch (suffix) {
            case "create" -> "add";
            case "update", "table-edit" -> "edit";
            case "view" -> "view";
            case "delete", "table-delete" -> "delete";
            default -> suffix;
        };
    }

    private String iconOf(PermSpec spec) {
        if (spec.type() != PermissionType.BUTTON) return null;
        String suffix = spec.code().substring(spec.code().lastIndexOf(':') + 1);
        return switch (suffix) {
            case "create" -> "Plus";
            case "update" -> "Edit";
            case "view" -> "View";
            case "delete" -> "Delete";
            default -> null;
        };
    }

    private String colorOf(PermSpec spec) {
        if (spec.type() == PermissionType.API) return null;
        String suffix = spec.code().substring(spec.code().lastIndexOf(':') + 1);
        if ("delete".equals(suffix) || "table-delete".equals(suffix)) return "danger";
        return "primary";
    }

    private static String sqlNullable(String value) {
        return value == null ? "NULL" : "'" + escapeSql(value) + "'";
    }

    private static String escapeSql(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    // ---------- README ----------

    private String readmeCrud(
            String title,
            String prefix,
            String pascal,
            String table,
            String apiBase,
            String viewPath,
            String routePath) {
        return """
                # %s — 路由脚手架（标准 CRUD）

                标准字段：`code` / `name` / `sort` / `status` / `remark`。表名：`%s`。

                ## 使用步骤

                1. 将 ZIP 内文件按路径拷贝到工程（覆盖前请确认无同名冲突）
                   - 后端 → `xn-admin-cloud/xn-system/...`
                   - 前端 → `xn-admin-vue3-ts/src/...`
                2. 开发环境重启 **xn-system**（`ddl-auto:update` 会按 Entity 自动建表）
                   - 生产建议执行 `sql/%s-schema.sql`，并将 `ddl-auto` 设为 `validate`
                3. 刷新浏览器；打开菜单 `%s`（`%s`）试用增删改查
                4. 按业务改字段：同步改 Entity / Request / VO / Repository / Vue 列与表单

                ## 生成信息

                | 项 | 值 |
                |----|----|
                | 模块前缀 | `%s` |
                | API | `%s` |
                | Entity | `%s` |
                | 视图 | `views/%s` |

                权限码示例：`%s:create`、`%s:view`、`%s:update`、`%s:delete`。
                """
                .formatted(
                        title, table, prefix, title, routePath, prefix, apiBase, pascal, viewPath,
                        prefix, prefix, prefix, prefix);
    }

    // ---------- Java templates ----------

    private String entityJava(String pascal, String table, String title) {
        return """
                package com.smartadmin.entity;

                import jakarta.persistence.Column;
                import jakarta.persistence.Entity;
                import jakarta.persistence.GeneratedValue;
                import jakarta.persistence.GenerationType;
                import jakarta.persistence.Id;
                import jakarta.persistence.Table;
                import lombok.Getter;
                import lombok.Setter;
                import org.hibernate.annotations.CreationTimestamp;
                import org.hibernate.annotations.UpdateTimestamp;

                import java.time.LocalDateTime;

                /**
                 * %s — 由路由代码生成（标准字段，可按业务扩展）。
                 */
                @Getter
                @Setter
                @Entity
                @Table(name = "%s")
                public class %s {

                    @Id
                    @GeneratedValue(strategy = GenerationType.IDENTITY)
                    private Long id;

                    @Column(nullable = false, unique = true, length = 50)
                    private String code;

                    @Column(nullable = false, length = 50)
                    private String name;

                    @Column(nullable = false)
                    private Integer sort = 0;

                    /** 1 启用 / 0 停用 */
                    @Column(nullable = false)
                    private Integer status = 1;

                    @Column(length = 200)
                    private String remark;

                    @CreationTimestamp
                    @Column(updatable = false)
                    private LocalDateTime createdAt;

                    @UpdateTimestamp
                    private LocalDateTime updatedAt;
                }
                """
                .formatted(title, table, pascal);
    }

    private String repositoryJava(String pascal) {
        return """
                package com.smartadmin.repository;

                import com.smartadmin.entity.%s;
                import org.springframework.data.domain.Page;
                import org.springframework.data.domain.Pageable;
                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.data.jpa.repository.Query;
                import org.springframework.data.repository.query.Param;

                public interface %sRepository extends JpaRepository<%s, Long> {

                    boolean existsByCode(String code);

                    @Query("SELECT e FROM %s e WHERE (:keyword = '' OR e.name LIKE CONCAT('%%', :keyword, '%%') OR e.code LIKE CONCAT('%%', :keyword, '%%'))"
                            + " AND (:status IS NULL OR e.status = :status)")
                    Page<%s> search(@Param("keyword") String keyword, @Param("status") Integer status, Pageable pageable);
                }
                """
                .formatted(pascal, pascal, pascal, pascal, pascal);
    }

    private String requestJava(String pascal, String title) {
        return """
                package com.smartadmin.dto;

                import jakarta.validation.constraints.NotBlank;
                import jakarta.validation.constraints.Pattern;
                import jakarta.validation.constraints.Size;
                import lombok.Data;

                @Data
                public class %sRequest {

                    @NotBlank(message = "编码不能为空")
                    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", message = "编码需以字母开头，只能包含字母、数字、下划线")
                    @Size(max = 50, message = "编码长度不能超过50")
                    private String code;

                    @NotBlank(message = "名称不能为空")
                    @Size(max = 50, message = "名称长度不能超过50")
                    private String name;

                    private Integer sort = 0;

                    private Integer status = 1;

                    @Size(max = 200, message = "备注长度不能超过200")
                    private String remark;
                }
                """
                .formatted(pascal);
    }

    private String voJava(String pascal) {
        return """
                package com.smartadmin.dto;

                import com.smartadmin.entity.%s;
                import lombok.Data;

                import java.time.LocalDateTime;

                @Data
                public class %sVO {

                    private Long id;
                    private String code;
                    private String name;
                    private Integer sort;
                    private Integer status;
                    private String remark;
                    private LocalDateTime createdAt;
                    private LocalDateTime updatedAt;

                    public static %sVO from(%s entity) {
                        %sVO vo = new %sVO();
                        vo.setId(entity.getId());
                        vo.setCode(entity.getCode());
                        vo.setName(entity.getName());
                        vo.setSort(entity.getSort());
                        vo.setStatus(entity.getStatus());
                        vo.setRemark(entity.getRemark());
                        vo.setCreatedAt(entity.getCreatedAt());
                        vo.setUpdatedAt(entity.getUpdatedAt());
                        return vo;
                    }
                }
                """
                .formatted(pascal, pascal, pascal, pascal, pascal, pascal);
    }

    private String controllerJava(String pascal, String camel, String apiBase, String title) {
        return """
                package com.smartadmin.controller;

                import com.smartadmin.common.ApiResponse;
                import com.smartadmin.common.OperLog;
                import com.smartadmin.dto.IdsRequest;
                import com.smartadmin.dto.PageResult;
                import com.smartadmin.dto.%sRequest;
                import com.smartadmin.dto.%sVO;
                import com.smartadmin.entity.OperBusinessType;
                import com.smartadmin.service.%sService;
                import jakarta.validation.Valid;
                import lombok.RequiredArgsConstructor;
                import org.springframework.web.bind.annotation.*;

                import java.util.Map;

                /**
                 * %s — 由路由代码生成（标准 CRUD，可按业务扩展）。
                 */
                @RestController
                @RequestMapping("%s")
                @RequiredArgsConstructor
                public class %sController {

                    private final %sService %sService;

                    @GetMapping
                    public ApiResponse<PageResult<%sVO>> list(
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "10") int size,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) Integer status) {
                        return ApiResponse.success(%sService.list(page, size, keyword, status));
                    }

                    @GetMapping("/{id}")
                    public ApiResponse<%sVO> detail(@PathVariable Long id) {
                        return ApiResponse.success(%sService.getById(id));
                    }

                    @PostMapping
                    @OperLog(title = "%s", businessType = OperBusinessType.INSERT)
                    public ApiResponse<%sVO> create(@Valid @RequestBody %sRequest request) {
                        return ApiResponse.success("创建成功", %sService.create(request));
                    }

                    @PutMapping("/{id}")
                    @OperLog(title = "%s", businessType = OperBusinessType.UPDATE)
                    public ApiResponse<%sVO> update(@PathVariable Long id, @Valid @RequestBody %sRequest request) {
                        return ApiResponse.success("更新成功", %sService.update(id, request));
                    }

                    @DeleteMapping("/{id}")
                    @OperLog(title = "%s", businessType = OperBusinessType.DELETE)
                    public ApiResponse<Void> delete(@PathVariable Long id) {
                        %sService.delete(id);
                        return ApiResponse.success("删除成功", null);
                    }

                    @PostMapping("/batch-delete")
                    @OperLog(title = "%s", businessType = OperBusinessType.DELETE)
                    public ApiResponse<Map<String, Integer>> batchDelete(@Valid @RequestBody IdsRequest request) {
                        int count = %sService.batchDelete(request.getIds());
                        return ApiResponse.success("删除成功", Map.of("count", count));
                    }
                }
                """
                .formatted(
                        pascal, pascal, pascal, title, apiBase, pascal, pascal, camel, pascal,
                        camel, pascal, camel, title, pascal, pascal, camel, title, pascal, pascal,
                        camel, title, camel, title, camel);
    }

    private String serviceJava(String pascal, String camel, String title, String prefix) {
        return """
                package com.smartadmin.service;

                import com.smartadmin.common.BusinessException;
                import com.smartadmin.dto.PageResult;
                import com.smartadmin.dto.%sRequest;
                import com.smartadmin.dto.%sVO;
                import com.smartadmin.entity.%s;
                import com.smartadmin.repository.%sRepository;
                import lombok.RequiredArgsConstructor;
                import org.springframework.data.domain.Page;
                import org.springframework.data.domain.PageRequest;
                import org.springframework.data.domain.Sort;
                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;
                import org.springframework.util.StringUtils;

                import java.util.List;

                /**
                 * %s Service — 由路由代码生成（标准 CRUD）。权限前缀：%s
                 */
                @Service
                @RequiredArgsConstructor
                public class %sService {

                    private final %sRepository %sRepository;
                    private final RbacService rbacService;

                    public PageResult<%sVO> list(int page, int size, String keyword, Integer status) {
                        rbacService.checkPermission("%s:view");
                        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "sort", "id"));
                        Page<%s> result = %sRepository.search(
                                StringUtils.hasText(keyword) ? keyword.trim() : "", status, pageable);
                        List<%sVO> records = result.getContent().stream().map(%sVO::from).toList();
                        return new PageResult<>(records, result.getTotalElements(), page, size);
                    }

                    public %sVO getById(Long id) {
                        rbacService.checkPermission("%s:view");
                        return %sVO.from(findEntity(id));
                    }

                    @Transactional
                    public %sVO create(%sRequest request) {
                        rbacService.checkPermission("%s:create");
                        if (%sRepository.existsByCode(request.getCode().trim())) {
                            throw new BusinessException("编码已存在");
                        }
                        %s entity = new %s();
                        applyRequest(entity, request);
                        return %sVO.from(%sRepository.save(entity));
                    }

                    @Transactional
                    public %sVO update(Long id, %sRequest request) {
                        rbacService.checkPermission("%s:update");
                        %s entity = findEntity(id);
                        String newCode = request.getCode().trim();
                        if (!entity.getCode().equalsIgnoreCase(newCode) && %sRepository.existsByCode(newCode)) {
                            throw new BusinessException("编码已存在");
                        }
                        applyRequest(entity, request);
                        return %sVO.from(%sRepository.save(entity));
                    }

                    @Transactional
                    public void delete(Long id) {
                        rbacService.checkPermission("%s:delete");
                        %sRepository.delete(findEntity(id));
                    }

                    @Transactional
                    public int batchDelete(List<Long> ids) {
                        rbacService.checkPermission("%s:delete");
                        int count = 0;
                        for (Long id : ids) {
                            %sRepository.delete(findEntity(id));
                            count++;
                        }
                        return count;
                    }

                    private void applyRequest(%s entity, %sRequest request) {
                        entity.setCode(request.getCode().trim());
                        entity.setName(request.getName().trim());
                        entity.setSort(request.getSort() != null ? request.getSort() : 0);
                        entity.setStatus(request.getStatus() != null ? request.getStatus() : 1);
                        entity.setRemark(request.getRemark());
                    }

                    private %s findEntity(Long id) {
                        return %sRepository.findById(id)
                                .orElseThrow(() -> new BusinessException("记录不存在"));
                    }
                }
                """
                .formatted(
                        pascal, pascal, pascal, pascal, title, prefix, pascal, pascal, camel,
                        pascal, prefix, pascal, camel, pascal, pascal, pascal, prefix, pascal,
                        pascal, pascal, prefix, camel, pascal, pascal, pascal, camel, pascal,
                        pascal, prefix, pascal, camel, pascal, camel, prefix, camel, prefix, camel,
                        pascal, pascal, pascal, camel);
    }

    // ---------- Vue templates ----------

    private String crudIndexVue(String title, String routePath, String apiModule, String prefix) {
        return """
                <template>
                  <xnPageLayout
                    v-model:view-mode="viewMode"
                    v-model:page="page"
                    v-model:page-size="size"
                    :show-pagination="true"
                    :total="total"
                    :loading="loading"
                    @page-change="loadData"
                  >
                    <template #search>
                      <xnSearch :search-item="searchItems" @query-form="inquires" @reset="reset" />
                    </template>

                    <template #toolbar>
                      <xnButton :list-item="buttonItems" :selected="selected" @button-click="buttonClick" />
                    </template>

                    <template #table>
                      <xnTable
                        v-model:page="page"
                        v-model:page-size="size"
                        :data="tableData"
                        :total="total"
                        :loading="loading"
                        table-key="%s"
                        entity-name="%s"
                        name-field="name"
                        :columns="columns"
                        :action-items="tableButtonItems"
                        stripe
                        @selection-change="(rows) => (selected = rows as any[])"
                        @page-change="loadData"
                      >
                        <template #status="{ row }">
                          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                            {{ row.status === 1 ? '启用' : '停用' }}
                          </el-tag>
                        </template>
                        <template #actions="{ row }">
                          <xnTableActions
                            :items="tableButtonItems"
                            :row="row"
                            @action-click="onTableAction"
                          />
                        </template>
                      </xnTable>
                    </template>
                  </xnPageLayout>

                  <SaveDialog ref="saveRef" @success="loadData" />
                </template>

                <script setup lang="ts">
                import { onMounted, ref } from 'vue'
                import { ElMessage, ElMessageBox } from 'element-plus'
                import xnPageLayout from '@/components/xnPageLayout/xnPageLayout.vue'
                import xnSearch from '@/components/xnSearch/xnSearch.vue'
                import xnButton from '@/components/xnButton/xnButton.vue'
                import xnTableActions from '@/components/xnButton/xnTableActions.vue'
                import xnTable from '@/components/xnTable/xnTable.vue'
                import SaveDialog from './save.vue'
                import { usePageUi } from '@/composables/usePageUi'
                import { list, batchRemove, remove } from '@/api/%s'
                import type { SearchForm } from '@/types/search'
                import type { SaveMode } from '@/types/save'
                import type { TableColumnItem } from '@/types/table'

                defineOptions({ name: '%s' })

                const { searchItems, buttonItems, tableButtonItems } = usePageUi('%s')
                const saveRef = ref<InstanceType<typeof SaveDialog>>()
                const loading = ref(false)
                const tableData = ref<any[]>([])
                const total = ref(0)
                const page = ref(1)
                const size = ref(10)
                const queryForm = ref<SearchForm>({})
                const viewMode = ref<'table' | 'card'>('table')
                const selected = ref<any[]>([])

                const columns: TableColumnItem[] = [
                  { type: 'selection', width: 50, fixed: true },
                  { prop: 'id', label: 'ID', width: 80 },
                  { prop: 'code', label: '编码', minWidth: 120 },
                  { prop: 'name', label: '名称', minWidth: 160 },
                  { prop: 'sort', label: '排序', width: 90 },
                  { type: 'slot', slot: 'status', label: '状态', width: 90 },
                  { prop: 'remark', label: '备注', minWidth: 140, showOverflowTooltip: true },
                  { type: 'slot', slot: 'actions', label: '操作', fixed: 'right', width: 140 },
                ]

                function openSave(mode: SaveMode, id?: number) {
                  saveRef.value?.open(mode, id)
                }

                async function loadData() {
                  loading.value = true
                  try {
                    const res = await list({
                      page: page.value - 1,
                      size: size.value,
                      keyword: String(queryForm.value.FuzzyWord ?? '').trim() || undefined,
                    })
                    tableData.value = res.data.records
                    total.value = res.data.total
                  } finally {
                    loading.value = false
                  }
                }

                async function inquires(form: SearchForm) {
                  queryForm.value = form
                  page.value = 1
                  await loadData()
                }

                async function reset() {
                  queryForm.value = {}
                  page.value = 1
                  await loadData()
                }

                async function handleDelete(row: any) {
                  await ElMessageBox.confirm(`确认删除「${row.name ?? row.id}」？`, '提示', { type: 'warning' })
                  await remove(row.id)
                  ElMessage.success('删除成功')
                  await loadData()
                }

                async function handleBatchDelete() {
                  if (!selected.value.length) {
                    ElMessage.warning('请先选择数据')
                    return
                  }
                  await ElMessageBox.confirm(`确认删除选中的 ${selected.value.length} 条？`, '提示', { type: 'warning' })
                  await batchRemove(selected.value.map((r) => r.id))
                  ElMessage.success('删除成功')
                  await loadData()
                }

                function buttonClick(action: string) {
                  switch (action) {
                    case 'add':
                      openSave('add')
                      break
                    case 'edit':
                      if (selected.value.length !== 1) {
                        ElMessage.warning('请选择一条记录')
                        return
                      }
                      openSave('edit', selected.value[0].id)
                      break
                    case 'view':
                      if (selected.value.length !== 1) {
                        ElMessage.warning('请选择一条记录')
                        return
                      }
                      openSave('view', selected.value[0].id)
                      break
                    case 'delete':
                      void handleBatchDelete()
                      break
                  }
                }

                function onTableAction(payload: { action: string; row: Record<string, any> }) {
                  const row = payload.row
                  switch (payload.action) {
                    case 'edit':
                      openSave('edit', row.id)
                      break
                    case 'delete':
                      void handleDelete(row)
                      break
                  }
                }

                onMounted(loadData)
                </script>
                """
                .formatted(
                        routePath.replace('/', ':').replaceAll("^:", ""),
                        title,
                        apiModule,
                        toPascal(prefix) + "Page",
                        routePath);
    }

    private String crudSaveVue(String title, String apiModule, String pascal) {
        return """
                <template>
                  <el-dialog
                    v-model="visible"
                    :title="dialogTitle"
                    width="520px"
                    destroy-on-close
                    @closed="handleClosed"
                  >
                    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" :disabled="mode === 'view'">
                      <el-form-item label="名称" prop="name">
                        <el-input v-model="form.name" maxlength="50" />
                      </el-form-item>
                      <el-form-item label="编码" prop="code">
                        <el-input
                          v-model="form.code"
                          :disabled="mode === 'view'"
                          maxlength="50"
                          placeholder="字母开头，如 demo01"
                        />
                      </el-form-item>
                      <el-form-item label="排序" prop="sort">
                        <el-input-number v-model="form.sort" :min="0" :max="9999" />
                      </el-form-item>
                      <el-form-item label="状态" prop="status">
                        <el-radio-group v-model="form.status">
                          <el-radio :value="1">启用</el-radio>
                          <el-radio :value="0">停用</el-radio>
                        </el-radio-group>
                      </el-form-item>
                      <el-form-item label="备注" prop="remark">
                        <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="200" />
                      </el-form-item>
                    </el-form>
                    <template #footer>
                      <el-button @click="visible = false">{{ mode === 'view' ? '关闭' : '取消' }}</el-button>
                      <el-button v-if="mode !== 'view'" type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
                    </template>
                  </el-dialog>
                </template>

                <script setup lang="ts">
                import { computed, reactive, ref } from 'vue'
                import type { FormInstance, FormRules } from 'element-plus'
                import { ElMessage } from 'element-plus'
                import { create, get, update } from '@/api/%s'
                import { saveDialogTitle, type SaveMode } from '@/types/save'

                defineOptions({ name: '%sSave' })

                const emit = defineEmits<{ success: [] }>()

                const visible = ref(false)
                const mode = ref<SaveMode>('add')
                const editingId = ref<number | null>(null)
                const submitting = ref(false)
                const formRef = ref<FormInstance>()

                const dialogTitle = computed(() => saveDialogTitle(mode.value, '%s'))

                const form = reactive({
                  code: '',
                  name: '',
                  sort: 0,
                  status: 1 as number,
                  remark: '',
                })

                const rules: FormRules = {
                  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
                  code: [
                    { required: true, message: '请输入编码', trigger: 'blur' },
                    {
                      pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/,
                      message: '需以字母开头，只能包含字母、数字、下划线',
                      trigger: 'blur',
                    },
                  ],
                }

                function resetForm() {
                  form.code = ''
                  form.name = ''
                  form.sort = 0
                  form.status = 1
                  form.remark = ''
                  editingId.value = null
                  formRef.value?.clearValidate()
                }

                async function loadDetail(id: number) {
                  const res = await get(id)
                  form.code = res.data.code ?? ''
                  form.name = res.data.name ?? ''
                  form.sort = res.data.sort ?? 0
                  form.status = res.data.status ?? 1
                  form.remark = res.data.remark ?? ''
                }

                async function open(openMode: SaveMode, id?: number) {
                  mode.value = openMode
                  resetForm()
                  editingId.value = id ?? null
                  visible.value = true
                  if (openMode !== 'add' && id) {
                    await loadDetail(id)
                  }
                }

                async function handleSubmit() {
                  if (!formRef.value) return
                  await formRef.value.validate(async (valid) => {
                    if (!valid) return
                    submitting.value = true
                    try {
                      if (mode.value === 'edit' && editingId.value) {
                        await update(editingId.value, { ...form })
                        ElMessage.success('更新成功')
                      } else {
                        await create({ ...form })
                        ElMessage.success('创建成功')
                      }
                      visible.value = false
                      emit('success')
                    } finally {
                      submitting.value = false
                    }
                  })
                }

                function handleClosed() {
                  resetForm()
                }

                defineExpose({ open })
                </script>
                """
                .formatted(apiModule, pascal, title);
    }

    private String apiTs(String apiModule, String apiUrl) {
        return """
                import request from '@/utils/request'
                import type { ApiResponse, PageResult } from '@/types'

                export type ListParams = { page: number; size: number; keyword?: string; status?: number }

                /** 分页列表 */
                export function list(params?: ListParams) {
                  return request.get<any, ApiResponse<PageResult<any>>>('%s', { params })
                }

                /** 详情 */
                export function get(id: number) {
                  return request.get<any, ApiResponse<any>>(`%s/${id}`)
                }

                /** 新增 */
                export function create(data: Record<string, unknown>) {
                  return request.post<any, ApiResponse<any>>('%s', data)
                }

                /** 更新 */
                export function update(id: number, data: Record<string, unknown>) {
                  return request.put<any, ApiResponse<any>>(`%s/${id}`, data)
                }

                /** 删除 */
                export function remove(id: number) {
                  return request.delete<any, ApiResponse<void>>(`%s/${id}`)
                }

                /** 批量删除 */
                export function batchRemove(ids: number[]) {
                  return request.post<any, ApiResponse<{ count: number }>>('%s/batch-delete', { ids })
                }
                """
                .formatted(apiUrl, apiUrl, apiUrl, apiUrl, apiUrl, apiUrl);
    }

    // ---------- specs ----------

    private record PermSpec(
            String code, String name, PermissionType type, String method, String path, int sort) {}

    private static PermSpec button(String code, String name, int sort) {
        return new PermSpec(code, name, PermissionType.BUTTON, null, null, sort);
    }

    private static PermSpec tableButton(String code, String name, int sort) {
        return new PermSpec(code, name, PermissionType.TABLE_BUTTON, null, null, sort);
    }

    private static PermSpec api(String code, String name, String method, String path, int sort) {
        return new PermSpec(code, name, PermissionType.API, method, path, sort);
    }
}
