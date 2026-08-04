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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

@Service
@RequiredArgsConstructor
public class RouteCodegenService {

    private final SysRouteRepository routeRepository;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final SysPageUiConfigRepository pageUiConfigRepository;
    private final RbacService rbacService;

    @Transactional
    public RouteCodegenVO generate(Long routeId, RouteCodegenRequest request) {
        rbacService.checkPermission("route:generate");

        SysRoute route = routeRepository.findById(routeId)
                .orElseThrow(() -> new BusinessException("路由不存在"));
        if (route.getType() != RouteType.MENU) {
            throw new BusinessException("仅菜单类型可代码生成");
        }
        if (!StringUtils.hasText(route.getPath()) || !StringUtils.hasText(route.getViewPath())) {
            throw new BusinessException("菜单缺少访问路径或视图目录");
        }

        String prefix = normalizePrefix(request.getModulePrefix());
        String apiBase = normalizeApiBase(request.getApiBasePath());
        RouteCodegenRequest.Template template = request.getTemplate() != null
                ? request.getTemplate()
                : RouteCodegenRequest.Template.CRUD;
        boolean persistPerms = request.getPersistPermissions() == null || request.getPersistPermissions();
        boolean genPageUi = request.getGeneratePageUi() == null || request.getGeneratePageUi();

        Permission menuPerm = resolveMenuPermission(route);

        List<PermSpec> specs = buildPermissionSpecs(template, prefix, apiBase, route.getTitle());
        List<String> codes = specs.stream().map(PermSpec::code).toList();

        int persisted = 0;
        StringBuilder sql = new StringBuilder();
        sql.append("-- 路由代码生成：").append(route.getTitle()).append(" (").append(route.getPath()).append(")\n");
        sql.append("-- 模板=").append(template).append(" 前缀=").append(prefix).append(" API=").append(apiBase).append("\n\n");

        for (PermSpec spec : specs) {
            sql.append(toInsertSql(spec, menuPerm));
            if (persistPerms) {
                if (upsertPermission(spec, menuPerm)) {
                    persisted++;
                }
            }
        }

        boolean pageUiPersisted = false;
        if (template != RouteCodegenRequest.Template.BLANK && genPageUi) {
            String searchJson = defaultSearchConfig(route.getTitle());
            sql.append("\n").append(toPageUiSql(route.getPath(), searchJson));
            if (persistPerms) {
                pageUiPersisted = upsertPageUi(route.getPath(), searchJson);
            }
        }

        List<RouteCodegenVO.GeneratedFile> files = buildFiles(template, route, prefix, apiBase);

        RouteCodegenVO vo = new RouteCodegenVO();
        vo.setRouteId(route.getId());
        vo.setRoutePath(route.getPath());
        vo.setViewPath(route.getViewPath());
        vo.setModulePrefix(prefix);
        vo.setApiBasePath(apiBase);
        vo.setTemplate(template.name());
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
        return permissionRepository.findByCode(route.getPermission())
                .orElseThrow(() -> new BusinessException("菜单权限不存在: " + route.getPermission()));
    }

    private List<PermSpec> buildPermissionSpecs(
            RouteCodegenRequest.Template template,
            String prefix,
            String apiBase,
            String title
    ) {
        List<PermSpec> list = new ArrayList<>();
        if (template == RouteCodegenRequest.Template.BLANK) {
            return list;
        }

        if (template == RouteCodegenRequest.Template.LIST) {
            list.add(button(prefix + ":view", "查看", 3));
            list.add(api("api:GET:" + apiBase, title + "列表接口", "GET", apiBase, 1));
            return list;
        }

        // CRUD
        list.add(button(prefix + ":create", "新增", 1));
        list.add(button(prefix + ":update", "编辑", 2));
        list.add(button(prefix + ":view", "查看", 3));
        list.add(button(prefix + ":delete", "删除", 4));
        list.add(tableButton(prefix + ":table-edit", "编辑", 1));
        list.add(tableButton(prefix + ":table-delete", "删除", 2));

        list.add(api("api:GET:" + apiBase, title + "列表接口", "GET", apiBase, 1));
        list.add(api("api:GET:" + apiBase + "/{id}", title + "详情接口", "GET", apiBase + "/{id}", 2));
        list.add(api("api:POST:" + apiBase, "创建" + title + "接口", "POST", apiBase, 3));
        list.add(api("api:PUT:" + apiBase + "/{id}", "更新" + title + "接口", "PUT", apiBase + "/{id}", 4));
        list.add(api("api:DELETE:" + apiBase + "/{id}", "删除" + title + "接口", "DELETE", apiBase + "/{id}", 5));
        list.add(api("api:POST:" + apiBase + "/batch-delete", "批量删除" + title, "POST", apiBase + "/batch-delete", 6));
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
            roleRepository.findByCode(roleCode).ifPresent(role -> {
                Role managed = roleRepository.findByIdWithPermissions(role.getId()).orElse(role);
                Set<Permission> perms = new HashSet<>(
                        managed.getPermissions() == null ? Set.of() : managed.getPermissions());
                if (perms.add(permission)) {
                    managed.setPermissions(perms);
                    roleRepository.save(managed);
                }
            });
        }
    }

    private List<RouteCodegenVO.GeneratedFile> buildFiles(
            RouteCodegenRequest.Template template,
            SysRoute route,
            String prefix,
            String apiBase
    ) {
        List<RouteCodegenVO.GeneratedFile> files = new ArrayList<>();
        String viewPath = route.getViewPath();
        String title = route.getTitle();
        String routePath = route.getPath();
        String pascal = toPascal(prefix);
        String apiModule = prefix;
        String apiUrl = apiBase.startsWith("/api/") ? apiBase.substring(4) : apiBase;

        if (template == RouteCodegenRequest.Template.BLANK) {
            files.add(RouteCodegenVO.GeneratedFile.of(
                    "xn-admin-vue3-ts/src/views/" + viewPath + "/index.vue",
                    blankIndexVue(title, routePath)
            ));
            return files;
        }

        if (template == RouteCodegenRequest.Template.LIST) {
            files.add(RouteCodegenVO.GeneratedFile.of(
                    "xn-admin-vue3-ts/src/views/" + viewPath + "/index.vue",
                    listIndexVue(title, routePath, apiModule, prefix)
            ));
            files.add(RouteCodegenVO.GeneratedFile.of(
                    "xn-admin-vue3-ts/src/api/" + apiModule + ".ts",
                    apiTs(apiModule, apiUrl, false)
            ));
            files.add(RouteCodegenVO.GeneratedFile.of(
                    "xn-admin-cloud/xn-system/src/main/java/com/smartadmin/controller/" + pascal + "Controller.java",
                    controllerJava(pascal, apiBase, title, prefix, false)
            ));
            return files;
        }

        // CRUD
        files.add(RouteCodegenVO.GeneratedFile.of(
                "xn-admin-vue3-ts/src/views/" + viewPath + "/index.vue",
                crudIndexVue(title, routePath, apiModule, prefix)
        ));
        files.add(RouteCodegenVO.GeneratedFile.of(
                "xn-admin-vue3-ts/src/views/" + viewPath + "/save.vue",
                crudSaveVue(title, apiModule)
        ));
        files.add(RouteCodegenVO.GeneratedFile.of(
                "xn-admin-vue3-ts/src/api/" + apiModule + ".ts",
                apiTs(apiModule, apiUrl, true)
        ));
        files.add(RouteCodegenVO.GeneratedFile.of(
                "xn-admin-cloud/xn-system/src/main/java/com/smartadmin/controller/" + pascal + "Controller.java",
                controllerJava(pascal, apiBase, title, prefix, true)
        ));
        files.add(RouteCodegenVO.GeneratedFile.of(
                "xn-admin-cloud/xn-system/src/main/java/com/smartadmin/service/" + pascal + "Service.java",
                serviceJava(pascal, title, prefix)
        ));
        return files;
    }

    private String buildZipBase64(List<RouteCodegenVO.GeneratedFile> files, String sql, String prefix) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                for (RouteCodegenVO.GeneratedFile file : files) {
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

    // ---------- naming / meta ----------

    static String normalizePrefix(String raw) {
        String v = raw.trim().toLowerCase(Locale.ROOT)
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

    /** 从路由 path 推导默认前缀：/system/orders → orders */
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
        String parentIdExpr = parent.getId() != null
                ? String.valueOf(parent.getId())
                : "(SELECT id FROM sys_permission WHERE code = '" + escapeSql(parent.getCode()) + "' LIMIT 1)";
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
                escapeSql(spec.code())
        );
    }

    private String toPageUiSql(String routePath, String searchJson) {
        return String.format(
                """
                INSERT INTO sys_page_ui_config (route_path, search_config, built_in)
                SELECT '%s', '%s', 0
                WHERE NOT EXISTS (SELECT 1 FROM sys_page_ui_config WHERE route_path = '%s');

                """,
                escapeSql(routePath),
                escapeSql(searchJson),
                escapeSql(routePath)
        );
    }

    private String defaultSearchConfig(String title) {
        return "[{\"label\":\"综合查询\",\"prop\":\"FuzzyWord\",\"type\":\"input\",\"placeholder\":\"搜索" + title + "\"}]";
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

    // ---------- templates ----------

    private String blankIndexVue(String title, String routePath) {
        return """
                <template>
                  <PageLayout>
                    <template #table>
                      <el-empty description="%s（请在此实现页面内容）" />
                    </template>
                  </PageLayout>
                </template>

                <script setup lang="ts">
                import PageLayout from '@/components/PageLayout/PageLayout.vue'

                defineOptions({ name: '%s' })
                </script>
                """.formatted(title, toPascal(defaultPrefixFromPath(routePath)) + "Page");
    }

    private String listIndexVue(String title, String routePath, String apiModule, String prefix) {
        return """
                <template>
                  <PageLayout
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
                        :columns="columns"
                        stripe
                        @selection-change="(rows) => (selected = rows as any[])"
                        @page-change="loadData"
                      />
                    </template>
                  </PageLayout>
                </template>

                <script setup lang="ts">
                import { onMounted, ref } from 'vue'
                import { ElMessage } from 'element-plus'
                import PageLayout from '@/components/PageLayout/PageLayout.vue'
                import xnSearch from '@/components/xnSearch/xnSearch.vue'
                import xnButton from '@/components/xnButton/xnButton.vue'
                import xnTable from '@/components/xnTable/xnTable.vue'
                import { usePageUi } from '@/composables/usePageUi'
                import { list } from '@/api/%s'
                import type { SearchForm } from '@/types/search'
                import type { TableColumnItem } from '@/types/table'

                defineOptions({ name: '%s' })

                const { searchItems, buttonItems } = usePageUi('%s')
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
                  { prop: 'name', label: '名称', minWidth: 160 },
                  // TODO: 按业务补充列
                ]

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

                function buttonClick(action: string) {
                  if (action === 'view') {
                    if (selected.value.length !== 1) {
                      ElMessage.warning('请选择一条记录')
                      return
                    }
                    ElMessage.info('请实现查看逻辑')
                  }
                }

                onMounted(loadData)
                </script>
                """.formatted(
                routePath.replace('/', ':').replaceAll("^:", ""),
                title,
                apiModule,
                toPascal(prefix) + "List",
                routePath
        );
    }

    private String crudIndexVue(String title, String routePath, String apiModule, String prefix) {
        return """
                <template>
                  <PageLayout
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
                        <template #actions="{ row }">
                          <xnTableActions
                            :items="tableButtonItems"
                            :row="row"
                            @action-click="onTableAction"
                          />
                        </template>
                      </xnTable>
                    </template>
                  </PageLayout>

                  <SaveDialog ref="saveRef" @success="loadData" />
                </template>

                <script setup lang="ts">
                import { onMounted, ref } from 'vue'
                import { ElMessage, ElMessageBox } from 'element-plus'
                import PageLayout from '@/components/PageLayout/PageLayout.vue'
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
                  { prop: 'name', label: '名称', minWidth: 160 },
                  // TODO: 按业务补充列
                  { type: 'slot', slot: 'actions', label: '操作', fixed: 'right' },
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
                """.formatted(
                routePath.replace('/', ':').replaceAll("^:", ""),
                title,
                apiModule,
                toPascal(prefix) + "Page",
                routePath
        );
    }

    private String crudSaveVue(String title, String apiModule) {
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
                        <el-input v-model="form.name" />
                      </el-form-item>
                      <!-- TODO: 按业务补充表单字段 -->
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

                const form = reactive<{ name: string }>({ name: '' })

                const rules: FormRules = {
                  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
                }

                function resetForm() {
                  form.name = ''
                  editingId.value = null
                  formRef.value?.clearValidate()
                }

                async function loadDetail(id: number) {
                  const res = await get(id)
                  form.name = res.data.name ?? ''
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
                        await update(editingId.value, form)
                        ElMessage.success('更新成功')
                      } else {
                        await create(form)
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
                """.formatted(apiModule, toPascal(apiModule), title);
    }

    private String apiTs(String apiModule, String apiUrl, boolean fullCrud) {
        String base = """
                import request from '@/utils/request'
                import type { ApiResponse, PageResult } from '@/types'

                export type ListParams = { page: number; size: number; keyword?: string }

                /** 分页列表 */
                export function list(params?: ListParams) {
                  return request.get<any, ApiResponse<PageResult<any>>>('%s', { params })
                }
                """.formatted(apiUrl);
        if (!fullCrud) {
            return base;
        }
        return base + """

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
                """.formatted(apiUrl, apiUrl, apiUrl, apiUrl, apiUrl);
    }

    private String controllerJava(String pascal, String apiBase, String title, String prefix, boolean fullCrud) {
        if (!fullCrud) {
            return """
                    package com.smartadmin.controller;

                    import com.smartadmin.common.ApiResponse;
                    import com.smartadmin.dto.PageResult;
                    import lombok.RequiredArgsConstructor;
                    import org.springframework.web.bind.annotation.GetMapping;
                    import org.springframework.web.bind.annotation.RequestMapping;
                    import org.springframework.web.bind.annotation.RequestParam;
                    import org.springframework.web.bind.annotation.RestController;

                    import java.util.List;
                    import java.util.Map;

                    /**
                     * %s — 由路由代码生成，请按业务补全 Service / Entity。
                     */
                    @RestController
                    @RequestMapping("%s")
                    @RequiredArgsConstructor
                    public class %sController {

                        @GetMapping
                        public ApiResponse<PageResult<Map<String, Object>>> list(
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "10") int size,
                                @RequestParam(required = false) String keyword) {
                            // TODO: 注入 Service 并实现分页查询；权限码 %s:view
                            return ApiResponse.success(new PageResult<>(List.of(), 0, page, size));
                        }
                    }
                    """.formatted(title, apiBase, pascal, prefix);
        }
        return """
                package com.smartadmin.controller;

                import com.smartadmin.common.ApiResponse;
                import com.smartadmin.common.OperLog;
                import com.smartadmin.dto.IdsRequest;
                import com.smartadmin.dto.PageResult;
                import com.smartadmin.entity.OperBusinessType;
                import com.smartadmin.service.%sService;
                import jakarta.validation.Valid;
                import lombok.RequiredArgsConstructor;
                import org.springframework.web.bind.annotation.*;

                import java.util.Map;

                /**
                 * %s — 由路由代码生成，请按业务补全 Entity / DTO。
                 */
                @RestController
                @RequestMapping("%s")
                @RequiredArgsConstructor
                public class %sController {

                    private final %sService %sService;

                    @GetMapping
                    public ApiResponse<PageResult<Map<String, Object>>> list(
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "10") int size,
                            @RequestParam(required = false) String keyword) {
                        return ApiResponse.success(%sService.list(page, size, keyword));
                    }

                    @GetMapping("/{id}")
                    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
                        return ApiResponse.success(%sService.getById(id));
                    }

                    @PostMapping
                    @OperLog(title = "%s", businessType = OperBusinessType.INSERT)
                    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
                        return ApiResponse.success("创建成功", %sService.create(body));
                    }

                    @PutMapping("/{id}")
                    @OperLog(title = "%s", businessType = OperBusinessType.UPDATE)
                    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
                        return ApiResponse.success("更新成功", %sService.update(id, body));
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
                """.formatted(
                pascal, title, apiBase, pascal, pascal,
                Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1),
                Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1),
                Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1),
                title,
                Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1),
                title,
                Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1),
                title,
                Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1),
                title,
                Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1)
        );
    }

    private String serviceJava(String pascal, String title, String prefix) {
        return """
                package com.smartadmin.service;

                import com.smartadmin.common.BusinessException;
                import com.smartadmin.dto.PageResult;
                import lombok.RequiredArgsConstructor;
                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;

                import java.util.List;
                import java.util.Map;

                /**
                 * %s Service — 由路由代码生成骨架，请替换为真实 Entity / Repository。
                 * 按钮权限前缀：%s
                 */
                @Service
                @RequiredArgsConstructor
                public class %sService {

                    private final RbacService rbacService;

                    public PageResult<Map<String, Object>> list(int page, int size, String keyword) {
                        rbacService.checkPermission("%s:view");
                        // TODO: 分页查询
                        return new PageResult<>(List.of(), 0, page, size);
                    }

                    public Map<String, Object> getById(Long id) {
                        rbacService.checkPermission("%s:view");
                        throw new BusinessException("请实现详情查询");
                    }

                    @Transactional
                    public Map<String, Object> create(Map<String, Object> body) {
                        rbacService.checkPermission("%s:create");
                        throw new BusinessException("请实现创建逻辑");
                    }

                    @Transactional
                    public Map<String, Object> update(Long id, Map<String, Object> body) {
                        rbacService.checkPermission("%s:update");
                        throw new BusinessException("请实现更新逻辑");
                    }

                    @Transactional
                    public void delete(Long id) {
                        rbacService.checkPermission("%s:delete");
                        throw new BusinessException("请实现删除逻辑");
                    }

                    @Transactional
                    public int batchDelete(List<Long> ids) {
                        rbacService.checkPermission("%s:delete");
                        throw new BusinessException("请实现批量删除逻辑");
                    }
                }
                """.formatted(title, prefix, pascal, prefix, prefix, prefix, prefix, prefix, prefix);
    }

    // ---------- specs ----------

    private record PermSpec(
            String code,
            String name,
            PermissionType type,
            String method,
            String path,
            int sort
    ) {}

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
