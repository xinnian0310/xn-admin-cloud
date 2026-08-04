package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.TableCodegenRequest;
import com.smartadmin.dto.TableCodegenVO;
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
import com.smartadmin.util.CodegenNaming;
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
public class TableCodegenService {

    private static final String JAVA_BASE =
            "xn-admin-cloud/xn-system/src/main/java/com/smartadmin/";
    private static final String VUE_BASE = "xn-admin-vue3-ts/src/";

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final SysPageUiConfigRepository pageUiConfigRepository;
    private final SysRouteRepository routeRepository;
    private final RbacService rbacService;

    @Transactional
    public TableCodegenVO generate(TableCodegenRequest request) {
        rbacService.checkPermission("codegen:generate");

        String tableName = request.getTableName().trim();
        String prefix = CodegenNaming.normalizePrefix(request.getModulePrefix());
        String apiBase = CodegenNaming.normalizeApiBase(request.getApiBasePath());
        String pascal =
                StringUtils.hasText(request.getClassName())
                        ? request.getClassName().trim()
                        : CodegenNaming.toPascal(prefix);
        if (!pascal.matches("^[A-Z][A-Za-z0-9]*$")) {
            throw new BusinessException("类名需以大写字母开头，仅含字母数字");
        }
        String camel = CodegenNaming.toCamel(pascal);
        String menuTitle = request.getMenuTitle().trim();
        String menuPath = normalizePath(request.getMenuPath());
        String viewPath = request.getViewPath().trim().replace('\\', '/').replaceAll("^/+|/+$", "");

        List<Col> cols = request.getColumns().stream().map(this::toCol).toList();
        if (cols.stream().noneMatch(Col::pk)) {
            throw new BusinessException("请至少标记一列为主键");
        }
        Col pk = cols.stream().filter(Col::pk).findFirst().orElseThrow();

        boolean persistPerms =
                request.getPersistPermissions() == null || request.getPersistPermissions();
        boolean genPageUi = request.getGeneratePageUi() == null || request.getGeneratePageUi();
        boolean createMenu = request.getCreateMenu() == null || request.getCreateMenu();

        boolean menuCreated = false;
        SysRoute menuRoute = null;
        if (createMenu) {
            menuCreated =
                    ensureMenuRoute(
                            menuTitle, menuPath, viewPath, "menu:" + prefix.replace('-', ':'));
            menuRoute = routeRepository.findByPath(menuPath).orElse(null);
        }

        Permission menuPerm = resolveOrCreateMenuPermission(menuRoute, menuTitle, menuPath, prefix);

        List<PermSpec> specs = buildPermissionSpecs(prefix, apiBase, menuTitle);
        List<String> codes = specs.stream().map(PermSpec::code).toList();

        int persisted = 0;
        StringBuilder sql = new StringBuilder();
        sql.append("-- 表驱动代码生成：").append(tableName).append(" → ").append(pascal).append("\n");
        sql.append("-- 前缀=").append(prefix).append(" API=").append(apiBase).append("\n");
        sql.append("-- 表已存在，无需建表；以下为权限 SQL\n\n");

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
            String searchJson = buildSearchConfig(cols, menuTitle);
            sql.append("\n").append(toPageUiSql(menuPath, searchJson));
            if (persistPerms) {
                pageUiPersisted = upsertPageUi(menuPath, searchJson);
            }
        }

        List<TableCodegenVO.GeneratedFile> files =
                buildFiles(
                        tableName, prefix, pascal, camel, apiBase, menuTitle, menuPath, viewPath,
                        cols, pk);

        TableCodegenVO vo = new TableCodegenVO();
        vo.setTableName(tableName);
        vo.setModulePrefix(prefix);
        vo.setClassName(pascal);
        vo.setApiBasePath(apiBase);
        vo.setMenuPath(menuPath);
        vo.setViewPath(viewPath);
        vo.setPermissionCodes(new ArrayList<>(codes));
        vo.setPersistedPermissionCount(persisted);
        vo.setPageUiPersisted(pageUiPersisted);
        vo.setMenuCreated(menuCreated);
        vo.setSql(sql.toString());
        vo.setFiles(files);
        vo.setZipBase64(buildZipBase64(files, sql.toString(), prefix));
        return vo;
    }

    private Col toCol(TableCodegenRequest.TableCodegenColumnRequest c) {
        String field =
                StringUtils.hasText(c.getJavaField())
                        ? c.getJavaField().trim()
                        : CodegenNaming.columnToCamel(c.getColumnName());
        String javaType = StringUtils.hasText(c.getJavaType()) ? c.getJavaType().trim() : "String";
        String formType = StringUtils.hasText(c.getFormType()) ? c.getFormType().trim() : "input";
        String label = StringUtils.hasText(c.getLabel()) ? c.getLabel().trim() : c.getColumnName();
        return new Col(
                c.getColumnName().trim(),
                label,
                javaType,
                field,
                formType,
                c.isPk(),
                c.isNullable(),
                c.getColumnSize(),
                c.isListShow(),
                c.isQueryable(),
                c.isFormShow(),
                c.isRequired());
    }

    private String normalizePath(String raw) {
        String v = raw.trim().replace('\\', '/').replaceAll("/+", "/");
        if (!v.startsWith("/")) v = "/" + v;
        if (v.length() > 1 && v.endsWith("/")) v = v.substring(0, v.length() - 1);
        return v;
    }

    private boolean ensureMenuRoute(String title, String path, String viewPath, String permission) {
        if (routeRepository.findByPath(path).isPresent()) {
            return false;
        }
        SysRoute parent =
                routeRepository.findAll().stream()
                        .filter(
                                r ->
                                        r.getType() == RouteType.DIR
                                                && "menu:system:tools".equals(r.getPermission()))
                        .findFirst()
                        .or(
                                () ->
                                        routeRepository.findAll().stream()
                                                .filter(
                                                        r ->
                                                                r.getType() == RouteType.DIR
                                                                        && "menu:system"
                                                                                .equals(
                                                                                        r
                                                                                                .getPermission()))
                                                .findFirst())
                        .orElse(null);
        SysRoute route = new SysRoute();
        route.setTitle(title);
        route.setPath(path);
        route.setViewPath(viewPath);
        route.setIcon("Document");
        route.setPermission(permission);
        route.setParent(parent);
        route.setType(RouteType.MENU);
        route.setSort(90);
        route.setStatus(1);
        route.setHidden(false);
        route.setAffix(false);
        route.setPermissionControl(true);
        route.setBuiltIn(false);
        routeRepository.save(route);
        return true;
    }

    private Permission resolveOrCreateMenuPermission(
            SysRoute menuRoute, String title, String path, String prefix) {
        String code =
                menuRoute != null && StringUtils.hasText(menuRoute.getPermission())
                        ? menuRoute.getPermission()
                        : "menu:" + prefix.replace('-', ':');
        Permission existing = permissionRepository.findByCode(code).orElse(null);
        if (existing != null) {
            return existing;
        }
        Permission parent =
                permissionRepository
                        .findByCode("menu:system:tools")
                        .or(() -> permissionRepository.findByCode("menu:system"))
                        .orElse(null);
        Permission created = new Permission();
        created.setCode(code);
        created.setName(title);
        created.setType(PermissionType.MENU);
        created.setPath(path);
        created.setParent(parent);
        created.setSort(90);
        created.setBuiltIn(false);
        created = permissionRepository.save(created);
        grantToPrivilegedRoles(created);
        return created;
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

    private void applyButtonMeta(Permission p, String code) {
        String suffix = code.contains(":") ? code.substring(code.lastIndexOf(':') + 1) : code;
        switch (suffix) {
            case "create" -> {
                p.setAction("add");
                p.setIcon("Plus");
                p.setButtonColor("primary");
            }
            case "update" -> {
                p.setAction("edit");
                p.setIcon("Edit");
                p.setButtonColor("primary");
            }
            case "view" -> {
                p.setAction("view");
                p.setIcon("View");
                p.setButtonColor("primary");
            }
            case "delete" -> {
                p.setAction("delete");
                p.setIcon("Delete");
                p.setButtonColor("danger");
            }
            case "table-edit" -> {
                p.setAction("edit");
                p.setIcon(null);
                p.setButtonColor("primary");
            }
            case "table-delete" -> {
                p.setAction("delete");
                p.setIcon(null);
                p.setButtonColor("danger");
            }
            default -> {
                p.setAction(suffix);
                p.setIcon(null);
                p.setButtonColor("primary");
            }
        }
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

    private String buildSearchConfig(List<Col> cols, String title) {
        List<Col> queryCols = cols.stream().filter(Col::queryable).limit(3).toList();
        if (queryCols.isEmpty()) {
            return "[{\"label\":\"综合查询\",\"prop\":\"FuzzyWord\",\"type\":\"input\",\"placeholder\":\"搜索"
                    + escapeJson(title)
                    + "\"}]";
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Col c : queryCols) {
            if (!first) sb.append(',');
            first = false;
            String type =
                    "select".equals(c.formType()) && "status".equalsIgnoreCase(c.columnName())
                            ? "select"
                            : "input";
            sb.append("{\"label\":\"")
                    .append(escapeJson(c.label()))
                    .append("\",\"prop\":\"")
                    .append(escapeJson(c.javaField()))
                    .append("\",\"type\":\"")
                    .append(type)
                    .append("\",\"placeholder\":\"搜索")
                    .append(escapeJson(c.label()))
                    .append("\"}");
        }
        // 始终带综合查询
        sb.append(
                ",{\"label\":\"综合查询\",\"prop\":\"FuzzyWord\",\"type\":\"input\",\"placeholder\":\"关键词\"}");
        sb.append(']');
        return sb.toString();
    }

    private List<TableCodegenVO.GeneratedFile> buildFiles(
            String tableName,
            String prefix,
            String pascal,
            String camel,
            String apiBase,
            String title,
            String menuPath,
            String viewPath,
            List<Col> cols,
            Col pk) {
        String apiUrl = apiBase.startsWith("/api/") ? apiBase.substring(4) : apiBase;
        List<TableCodegenVO.GeneratedFile> files = new ArrayList<>();
        files.add(
                TableCodegenVO.GeneratedFile.of(
                        JAVA_BASE + "entity/" + pascal + ".java",
                        entityJava(pascal, tableName, title, cols, pk)));
        files.add(
                TableCodegenVO.GeneratedFile.of(
                        JAVA_BASE + "repository/" + pascal + "Repository.java",
                        repositoryJava(pascal, cols)));
        files.add(
                TableCodegenVO.GeneratedFile.of(
                        JAVA_BASE + "dto/" + pascal + "Request.java", requestJava(pascal, cols)));
        files.add(
                TableCodegenVO.GeneratedFile.of(
                        JAVA_BASE + "dto/" + pascal + "VO.java", voJava(pascal, cols)));
        files.add(
                TableCodegenVO.GeneratedFile.of(
                        JAVA_BASE + "service/" + pascal + "Service.java",
                        serviceJava(pascal, camel, title, prefix, cols, pk)));
        files.add(
                TableCodegenVO.GeneratedFile.of(
                        JAVA_BASE + "controller/" + pascal + "Controller.java",
                        controllerJava(pascal, camel, apiBase, title)));
        files.add(
                TableCodegenVO.GeneratedFile.of(
                        VUE_BASE + "views/" + viewPath + "/index.vue",
                        indexVue(title, menuPath, prefix, cols, pk)));
        files.add(
                TableCodegenVO.GeneratedFile.of(
                        VUE_BASE + "views/" + viewPath + "/save.vue",
                        saveVue(title, prefix, pascal, cols)));
        files.add(
                TableCodegenVO.GeneratedFile.of(VUE_BASE + "api/" + prefix + ".ts", apiTs(apiUrl)));
        files.add(
                TableCodegenVO.GeneratedFile.of(
                        "README.md",
                        readme(title, tableName, prefix, pascal, apiBase, viewPath, menuPath)));
        return files;
    }

    private String entityJava(String pascal, String table, String title, List<Col> cols, Col pk) {
        boolean needBigDecimal = cols.stream().anyMatch(c -> "BigDecimal".equals(c.javaType()));
        boolean needLocalDate = cols.stream().anyMatch(c -> "LocalDate".equals(c.javaType()));
        boolean needLocalDateTime =
                cols.stream().anyMatch(c -> "LocalDateTime".equals(c.javaType()));
        boolean needAuditAnno =
                cols.stream()
                        .anyMatch(
                                c -> {
                                    String l = c.columnName().toLowerCase(Locale.ROOT);
                                    return l.equals("created_at")
                                            || l.equals("updated_at")
                                            || l.equals("create_time")
                                            || l.equals("update_time");
                                });
        // pk 用于校验调用方已识别主键（字段循环内按 c.pk() 处理）
        if (pk == null) {
            throw new BusinessException("缺少主键列");
        }
        StringBuilder fields = new StringBuilder();
        for (Col c : cols) {
            if (c.pk()) {
                fields.append(
                        """

                            @Id
                            @GeneratedValue(strategy = GenerationType.IDENTITY)
                            @Column(name = "%s")
                            private %s %s;
                        """
                                .formatted(c.columnName(), c.javaType(), c.javaField()));
                continue;
            }
            String nullable = c.nullable() ? "" : ", nullable = false";
            String length =
                    "String".equals(c.javaType())
                                    && c.columnSize() != null
                                    && c.columnSize() > 0
                                    && c.columnSize() <= 4000
                            ? ", length = " + c.columnSize()
                            : "";
            String extraAnno = "";
            String lower = c.columnName().toLowerCase(Locale.ROOT);
            if (lower.equals("created_at") || lower.equals("create_time")) {
                extraAnno =
                        "\n    @CreationTimestamp\n    @Column(name = \""
                                + c.columnName()
                                + "\", updatable = false)\n";
                fields.append(extraAnno)
                        .append("    private ")
                        .append(c.javaType())
                        .append(' ')
                        .append(c.javaField())
                        .append(";\n");
                continue;
            }
            if (lower.equals("updated_at") || lower.equals("update_time")) {
                extraAnno =
                        "\n    @UpdateTimestamp\n    @Column(name = \"" + c.columnName() + "\")\n";
                fields.append(extraAnno)
                        .append("    private ")
                        .append(c.javaType())
                        .append(' ')
                        .append(c.javaField())
                        .append(";\n");
                continue;
            }
            fields.append("\n    @Column(name = \"")
                    .append(c.columnName())
                    .append('"')
                    .append(nullable)
                    .append(length)
                    .append(")\n");
            fields.append("    private ")
                    .append(c.javaType())
                    .append(' ')
                    .append(c.javaField())
                    .append(";\n");
        }

        StringBuilder imports =
                new StringBuilder(
                        """
                package com.smartadmin.entity;

                import jakarta.persistence.Column;
                import jakarta.persistence.Entity;
                import jakarta.persistence.GeneratedValue;
                import jakarta.persistence.GenerationType;
                import jakarta.persistence.Id;
                import jakarta.persistence.Table;
                import lombok.Getter;
                import lombok.Setter;
                """);
        if (needAuditAnno) {
            imports.append("import org.hibernate.annotations.CreationTimestamp;\n");
            imports.append("import org.hibernate.annotations.UpdateTimestamp;\n");
        }
        if (needBigDecimal) imports.append("import java.math.BigDecimal;\n");
        if (needLocalDate) imports.append("import java.time.LocalDate;\n");
        if (needLocalDateTime || needAuditAnno) {
            imports.append("import java.time.LocalDateTime;\n");
        }

        return imports
                + """

                /**
                 * %s — 表驱动代码生成（表 %s）。
                 */
                @Getter
                @Setter
                @Entity
                @Table(name = "%s")
                public class %s {
                %s}
                """
                        .formatted(title, table, table, pascal, fields);
    }

    private String repositoryJava(String pascal, List<Col> cols) {
        List<Col> stringCols =
                cols.stream()
                        .filter(c -> !c.pk() && "String".equals(c.javaType()))
                        .limit(3)
                        .toList();
        StringBuilder like = new StringBuilder("(:keyword = ''");
        if (stringCols.isEmpty()) {
            like.append(" OR CAST(e.")
                    .append(cols.get(0).javaField())
                    .append(" AS string) LIKE CONCAT('%', :keyword, '%')");
        } else {
            for (Col c : stringCols) {
                like.append(" OR e.")
                        .append(c.javaField())
                        .append(" LIKE CONCAT('%', :keyword, '%')");
            }
        }
        like.append(')');

        return """
                package com.smartadmin.repository;

                import com.smartadmin.entity.%s;
                import org.springframework.data.domain.Page;
                import org.springframework.data.domain.Pageable;
                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.data.jpa.repository.Query;
                import org.springframework.data.repository.query.Param;

                public interface %sRepository extends JpaRepository<%s, Long> {

                    @Query("SELECT e FROM %s e WHERE %s")
                    Page<%s> search(@Param("keyword") String keyword, Pageable pageable);
                }
                """
                .formatted(
                        pascal, pascal, pascal, pascal, like.toString().replace("%", "%%"), pascal);
    }

    private String requestJava(String pascal, List<Col> cols) {
        List<Col> formCols = cols.stream().filter(Col::formShow).toList();
        StringBuilder fields = new StringBuilder();
        StringBuilder imports =
                new StringBuilder(
                        """
                package com.smartadmin.dto;

                import jakarta.validation.constraints.NotBlank;
                import jakarta.validation.constraints.NotNull;
                import jakarta.validation.constraints.Size;
                import lombok.Data;
                """);
        boolean needBigDecimal = formCols.stream().anyMatch(c -> "BigDecimal".equals(c.javaType()));
        boolean needLocalDate = formCols.stream().anyMatch(c -> "LocalDate".equals(c.javaType()));
        boolean needLocalDateTime =
                formCols.stream().anyMatch(c -> "LocalDateTime".equals(c.javaType()));
        if (needBigDecimal) imports.append("import java.math.BigDecimal;\n");
        if (needLocalDate) imports.append("import java.time.LocalDate;\n");
        if (needLocalDateTime) imports.append("import java.time.LocalDateTime;\n");

        for (Col c : formCols) {
            if ("String".equals(c.javaType())) {
                if (c.required()) {
                    fields.append("\n    @NotBlank(message = \"")
                            .append(c.label())
                            .append("不能为空\")\n");
                }
                if (c.columnSize() != null && c.columnSize() > 0) {
                    fields.append("    @Size(max = ")
                            .append(c.columnSize())
                            .append(", message = \"")
                            .append(c.label())
                            .append("长度不能超过")
                            .append(c.columnSize())
                            .append("\")\n");
                }
            } else if (c.required()) {
                fields.append("\n    @NotNull(message = \"").append(c.label()).append("不能为空\")\n");
            } else {
                fields.append('\n');
            }
            fields.append("    private ")
                    .append(c.javaType())
                    .append(' ')
                    .append(c.javaField())
                    .append(";\n");
        }
        return imports
                + """

                @Data
                public class %sRequest {
                %s}
                """
                        .formatted(pascal, fields);
    }

    private String voJava(String pascal, List<Col> cols) {
        StringBuilder fields = new StringBuilder();
        StringBuilder assigns = new StringBuilder();
        boolean needBigDecimal = cols.stream().anyMatch(c -> "BigDecimal".equals(c.javaType()));
        boolean needLocalDate = cols.stream().anyMatch(c -> "LocalDate".equals(c.javaType()));
        boolean needLocalDateTime =
                cols.stream().anyMatch(c -> "LocalDateTime".equals(c.javaType()));
        StringBuilder imports =
                new StringBuilder(
                        """
                package com.smartadmin.dto;

                import com.smartadmin.entity.%s;
                import lombok.Data;
                """
                                .formatted(pascal));
        if (needBigDecimal) imports.append("import java.math.BigDecimal;\n");
        if (needLocalDate) imports.append("import java.time.LocalDate;\n");
        if (needLocalDateTime) imports.append("import java.time.LocalDateTime;\n");

        for (Col c : cols) {
            fields.append("    private ")
                    .append(c.javaType())
                    .append(' ')
                    .append(c.javaField())
                    .append(";\n");
            String cap =
                    Character.toUpperCase(c.javaField().charAt(0)) + c.javaField().substring(1);
            assigns.append("        vo.set")
                    .append(cap)
                    .append("(entity.get")
                    .append(cap)
                    .append("());\n");
        }
        return imports
                + """

                @Data
                public class %sVO {
                %s
                    public static %sVO from(%s entity) {
                        %sVO vo = new %sVO();
                %s        return vo;
                    }
                }
                """
                        .formatted(pascal, fields, pascal, pascal, pascal, pascal, assigns);
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

                @RestController
                @RequestMapping("%s")
                @RequiredArgsConstructor
                public class %sController {

                    private final %sService %sService;

                    @GetMapping
                    public ApiResponse<PageResult<%sVO>> list(
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "10") int size,
                            @RequestParam(required = false) String keyword) {
                        return ApiResponse.success(%sService.list(page, size, keyword));
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
                        pascal, pascal, pascal, apiBase, pascal, pascal, camel, pascal, camel,
                        pascal, camel, title, pascal, pascal, camel, title, pascal, pascal, camel,
                        title, camel, title, camel);
    }

    private String serviceJava(
            String pascal, String camel, String title, String prefix, List<Col> cols, Col pk) {
        List<Col> formCols = cols.stream().filter(Col::formShow).toList();
        StringBuilder apply = new StringBuilder();
        for (Col c : formCols) {
            String cap =
                    Character.toUpperCase(c.javaField().charAt(0)) + c.javaField().substring(1);
            if ("String".equals(c.javaType())) {
                apply.append("        entity.set")
                        .append(cap)
                        .append("(request.get")
                        .append(cap)
                        .append("() == null ? null : request.get")
                        .append(cap)
                        .append("().trim());\n");
            } else {
                apply.append("        entity.set")
                        .append(cap)
                        .append("(request.get")
                        .append(cap)
                        .append("());\n");
            }
        }
        String pkType = pk.javaType();
        String pkCap =
                Character.toUpperCase(pk.javaField().charAt(0)) + pk.javaField().substring(1);

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

                @Service
                @RequiredArgsConstructor
                public class %sService {

                    private final %sRepository %sRepository;
                    private final RbacService rbacService;

                    public PageResult<%sVO> list(int page, int size, String keyword) {
                        rbacService.checkPermission("%s:view");
                        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "%s"));
                        Page<%s> result = %sRepository.search(
                                StringUtils.hasText(keyword) ? keyword.trim() : "", pageable);
                        List<%sVO> records = result.getContent().stream().map(%sVO::from).toList();
                        return new PageResult<>(records, result.getTotalElements(), page, size);
                    }

                    public %sVO getById(%s id) {
                        rbacService.checkPermission("%s:view");
                        return %sVO.from(findEntity(id));
                    }

                    @Transactional
                    public %sVO create(%sRequest request) {
                        rbacService.checkPermission("%s:create");
                        %s entity = new %s();
                        applyRequest(entity, request);
                        return %sVO.from(%sRepository.save(entity));
                    }

                    @Transactional
                    public %sVO update(%s id, %sRequest request) {
                        rbacService.checkPermission("%s:update");
                        %s entity = findEntity(id);
                        applyRequest(entity, request);
                        return %sVO.from(%sRepository.save(entity));
                    }

                    @Transactional
                    public void delete(%s id) {
                        rbacService.checkPermission("%s:delete");
                        %sRepository.delete(findEntity(id));
                    }

                    @Transactional
                    public int batchDelete(List<%s> ids) {
                        rbacService.checkPermission("%s:delete");
                        int count = 0;
                        for (%s id : ids) {
                            %sRepository.delete(findEntity(id));
                            count++;
                        }
                        return count;
                    }

                    private void applyRequest(%s entity, %sRequest request) {
                %s    }

                    private %s findEntity(%s id) {
                        return %sRepository.findById(id)
                                .orElseThrow(() -> new BusinessException("记录不存在"));
                    }
                }
                """
                .formatted(
                        pascal,
                        pascal,
                        pascal,
                        pascal,
                        pascal,
                        pascal,
                        camel,
                        pascal,
                        prefix,
                        pk.javaField(),
                        pascal,
                        camel,
                        pascal,
                        pascal,
                        pascal,
                        pkType,
                        prefix,
                        pascal,
                        pascal,
                        pascal,
                        prefix,
                        pascal,
                        pascal,
                        pascal,
                        camel,
                        pascal,
                        pkType,
                        pascal,
                        prefix,
                        pascal,
                        pascal,
                        camel,
                        pkType,
                        prefix,
                        camel,
                        pkType,
                        prefix,
                        pkType,
                        camel,
                        pascal,
                        pascal,
                        apply,
                        pascal,
                        pkType,
                        camel);
    }

    private String indexVue(String title, String menuPath, String prefix, List<Col> cols, Col pk) {
        StringBuilder columnDefs = new StringBuilder();
        columnDefs.append("  { type: 'selection', width: 50, fixed: true },\n");
        for (Col c : cols) {
            if (!c.listShow()) continue;
            if ("status".equalsIgnoreCase(c.columnName()) && "select".equals(c.formType())) {
                columnDefs
                        .append("  { type: 'slot', slot: 'status', label: '")
                        .append(escapeJs(c.label()))
                        .append("', width: 90 },\n");
            } else {
                columnDefs
                        .append("  { prop: '")
                        .append(c.javaField())
                        .append("', label: '")
                        .append(escapeJs(c.label()))
                        .append("', minWidth: 120")
                        .append(c.pk() ? ", width: 80" : "")
                        .append(" },\n");
            }
        }
        columnDefs.append(
                "  { type: 'slot', slot: 'actions', label: '操作', fixed: 'right', width: 140 },");

        boolean hasStatus =
                cols.stream()
                        .anyMatch(c -> c.listShow() && "status".equalsIgnoreCase(c.columnName()));
        String statusSlot =
                hasStatus
                        ? """
                        <template #status="{ row }">
                          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                            {{ row.status === 1 ? '启用' : '停用' }}
                          </el-tag>
                        </template>
                """
                        : "";

        String nameField =
                cols.stream()
                        .filter(
                                c ->
                                        c.javaField().equals("name")
                                                || c.columnName()
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains("name"))
                        .map(Col::javaField)
                        .findFirst()
                        .orElse(pk.javaField());

        String tableKey = menuPath.replace('/', ':').replaceAll("^:", "");

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
                        name-field="%s"
                        :columns="columns"
                        :action-items="tableButtonItems"
                        stripe
                        @selection-change="(rows) => (selected = rows as any[])"
                        @page-change="loadData"
                      >
                %s        <template #actions="{ row }">
                          <xnTableActions :items="tableButtonItems" :row="row" @action-click="onTableAction" />
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
                %s
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
                  await ElMessageBox.confirm(`确认删除「${row.%s ?? row.%s}」？`, '提示', { type: 'warning' })
                  await remove(row.%s)
                  ElMessage.success('删除成功')
                  await loadData()
                }

                async function handleBatchDelete() {
                  if (!selected.value.length) {
                    ElMessage.warning('请先选择数据')
                    return
                  }
                  await ElMessageBox.confirm(`确认删除选中的 ${selected.value.length} 条？`, '提示', { type: 'warning' })
                  await batchRemove(selected.value.map((r) => r.%s))
                  ElMessage.success('删除成功')
                  await loadData()
                }

                function buttonClick(action: string) {
                  switch (action) {
                    case 'add': openSave('add'); break
                    case 'edit':
                      if (selected.value.length !== 1) { ElMessage.warning('请选择一条记录'); return }
                      openSave('edit', selected.value[0].%s); break
                    case 'view':
                      if (selected.value.length !== 1) { ElMessage.warning('请选择一条记录'); return }
                      openSave('view', selected.value[0].%s); break
                    case 'delete': void handleBatchDelete(); break
                  }
                }

                function onTableAction(payload: { action: string; row: Record<string, any> }) {
                  if (payload.action === 'edit') openSave('edit', payload.row.%s)
                  if (payload.action === 'delete') void handleDelete(payload.row)
                }

                onMounted(loadData)
                </script>
                """
                .formatted(
                        tableKey,
                        title,
                        nameField,
                        statusSlot,
                        prefix,
                        CodegenNaming.toPascal(prefix) + "Page",
                        menuPath,
                        columnDefs.toString(),
                        nameField,
                        pk.javaField(),
                        pk.javaField(),
                        pk.javaField(),
                        pk.javaField(),
                        pk.javaField(),
                        pk.javaField());
    }

    private String saveVue(String title, String prefix, String pascal, List<Col> cols) {
        List<Col> formCols = cols.stream().filter(Col::formShow).toList();
        StringBuilder formItems = new StringBuilder();
        StringBuilder formInit = new StringBuilder();
        StringBuilder resetBody = new StringBuilder();
        StringBuilder loadBody = new StringBuilder();
        StringBuilder rules = new StringBuilder();

        for (Col c : formCols) {
            formInit.append("  ")
                    .append(c.javaField())
                    .append(": ")
                    .append(defaultJsValue(c))
                    .append(",\n");
            resetBody
                    .append("  form.")
                    .append(c.javaField())
                    .append(" = ")
                    .append(defaultJsValue(c))
                    .append("\n");
            loadBody.append("  form.")
                    .append(c.javaField())
                    .append(" = res.data.")
                    .append(c.javaField())
                    .append(" ?? ")
                    .append(defaultJsValue(c))
                    .append("\n");
            if (c.required() && "String".equals(c.javaType())) {
                rules.append("  ")
                        .append(c.javaField())
                        .append(": [{ required: true, message: '请输入")
                        .append(escapeJs(c.label()))
                        .append("', trigger: 'blur' }],\n");
            }
            formItems.append(formItemVue(c));
        }

        return """
                <template>
                  <el-dialog
                    v-model="visible"
                    :title="dialogTitle"
                    width="560px"
                    destroy-on-close
                    @closed="handleClosed"
                  >
                    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" :disabled="mode === 'view'">
                %s    </el-form>
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
                %s})
                const rules: FormRules = {
                %s}

                function resetForm() {
                %s  editingId.value = null
                  formRef.value?.clearValidate()
                }

                async function loadDetail(id: number) {
                  const res = await get(id)
                %s}

                async function open(openMode: SaveMode, id?: number) {
                  mode.value = openMode
                  resetForm()
                  editingId.value = id ?? null
                  visible.value = true
                  if (openMode !== 'add' && id) await loadDetail(id)
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

                function handleClosed() { resetForm() }
                defineExpose({ open })
                </script>
                """
                .formatted(formItems, prefix, pascal, title, formInit, rules, resetBody, loadBody);
    }

    private String formItemVue(Col c) {
        String label = escapeJs(c.label());
        String prop = c.javaField();
        return switch (c.formType()) {
            case "number" ->
                    """
                      <el-form-item label="%s" prop="%s">
                        <el-input-number v-model="form.%s" :controls="true" style="width: 100%%" />
                      </el-form-item>
                    """
                            .formatted(label, prop, prop);
            case "textarea" ->
                    """
                      <el-form-item label="%s" prop="%s">
                        <el-input v-model="form.%s" type="textarea" :rows="3" />
                      </el-form-item>
                    """
                            .formatted(label, prop, prop);
            case "datetime" ->
                    """
                      <el-form-item label="%s" prop="%s">
                        <el-date-picker v-model="form.%s" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%%" />
                      </el-form-item>
                    """
                            .formatted(label, prop, prop);
            case "select" -> {
                if ("status".equalsIgnoreCase(c.columnName()) || "Integer".equals(c.javaType())) {
                    yield """
                              <el-form-item label="%s" prop="%s">
                                <el-radio-group v-model="form.%s">
                                  <el-radio :value="1">启用</el-radio>
                                  <el-radio :value="0">停用</el-radio>
                                </el-radio-group>
                              </el-form-item>
                            """
                            .formatted(label, prop, prop);
                }
                if ("Boolean".equals(c.javaType())) {
                    yield """
                              <el-form-item label="%s" prop="%s">
                                <el-switch v-model="form.%s" />
                              </el-form-item>
                            """
                            .formatted(label, prop, prop);
                }
                yield """
                          <el-form-item label="%s" prop="%s">
                            <el-input v-model="form.%s" />
                          </el-form-item>
                        """
                        .formatted(label, prop, prop);
            }
            default ->
                    """
                      <el-form-item label="%s" prop="%s">
                        <el-input v-model="form.%s" />
                      </el-form-item>
                    """
                            .formatted(label, prop, prop);
        };
    }

    private String defaultJsValue(Col c) {
        return switch (c.javaType()) {
            case "Integer", "Long", "BigDecimal" -> "0";
            case "Boolean" -> "false";
            default -> "''";
        };
    }

    private String apiTs(String apiUrl) {
        return """
                import request from '@/utils/request'
                import type { ApiResponse, PageResult } from '@/types'

                export type ListParams = { page: number; size: number; keyword?: string }

                export function list(params?: ListParams) {
                  return request.get<any, ApiResponse<PageResult<any>>>('%s', { params })
                }

                export function get(id: number) {
                  return request.get<any, ApiResponse<any>>(`%s/${id}`)
                }

                export function create(data: Record<string, unknown>) {
                  return request.post<any, ApiResponse<any>>('%s', data)
                }

                export function update(id: number, data: Record<string, unknown>) {
                  return request.put<any, ApiResponse<any>>(`%s/${id}`, data)
                }

                export function remove(id: number) {
                  return request.delete<any, ApiResponse<void>>(`%s/${id}`)
                }

                export function batchRemove(ids: number[]) {
                  return request.post<any, ApiResponse<{ count: number }>>('%s/batch-delete', { ids })
                }
                """
                .formatted(apiUrl, apiUrl, apiUrl, apiUrl, apiUrl, apiUrl);
    }

    private String readme(
            String title,
            String table,
            String prefix,
            String pascal,
            String apiBase,
            String viewPath,
            String menuPath) {
        return """
                # %s — 表驱动代码生成

                数据表：`%s` → Entity `%s`

                ## 使用步骤

                1. 按 ZIP 内路径拷贝到工程（前后端）
                2. 重启 **xn-system**
                3. 打开菜单 `%s`（`%s`）试用 CRUD
                4. 按业务继续改字段与校验

                | 项 | 值 |
                |----|----|
                | 前缀 | `%s` |
                | API | `%s` |
                | 视图 | `views/%s` |
                """
                .formatted(title, table, pascal, title, menuPath, prefix, apiBase, viewPath);
    }

    private String buildZipBase64(
            List<TableCodegenVO.GeneratedFile> files, String sql, String prefix) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                for (TableCodegenVO.GeneratedFile file : files) {
                    zos.putNextEntry(new ZipEntry(file.getPath()));
                    zos.write(file.getContent().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
                zos.putNextEntry(new ZipEntry("sql/" + prefix + "-permissions.sql"));
                zos.write(sql.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new BusinessException("打包生成文件失败");
        }
    }

    private String toInsertSql(PermSpec spec, Permission parent) {
        String parentIdExpr =
                parent.getId() != null
                        ? String.valueOf(parent.getId())
                        : "(SELECT id FROM sys_permission WHERE code = '"
                                + escapeSql(parent.getCode())
                                + "' LIMIT 1)";
        String action = null;
        String icon = null;
        String color = null;
        if (spec.type() != PermissionType.API) {
            String suffix = spec.code().substring(spec.code().lastIndexOf(':') + 1);
            action =
                    switch (suffix) {
                        case "create" -> "add";
                        case "update", "table-edit" -> "edit";
                        case "view" -> "view";
                        case "delete", "table-delete" -> "delete";
                        default -> suffix;
                    };
            if (spec.type() == PermissionType.BUTTON) {
                icon =
                        switch (suffix) {
                            case "create" -> "Plus";
                            case "update" -> "Edit";
                            case "view" -> "View";
                            case "delete" -> "Delete";
                            default -> null;
                        };
            }
            color =
                    ("delete".equals(suffix) || "table-delete".equals(suffix))
                            ? "danger"
                            : "primary";
        }
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
                sqlNullable(action),
                sqlNullable(icon),
                sqlNullable(color),
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

    private static String sqlNullable(String value) {
        return value == null ? "NULL" : "'" + escapeSql(value) + "'";
    }

    private static String escapeSql(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escapeJs(String value) {
        return value == null ? "" : value.replace("'", "\\'");
    }

    private record Col(
            String columnName,
            String label,
            String javaType,
            String javaField,
            String formType,
            boolean pk,
            boolean nullable,
            Integer columnSize,
            boolean listShow,
            boolean queryable,
            boolean formShow,
            boolean required) {}

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
