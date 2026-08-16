package com.smartadmin.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 多前端栈代码生成：仅产出前端文件（index / save / api）。 Java 端仍由各 CodegenService 负责。 */
public final class CodegenFrontendEmitter {

    private CodegenFrontendEmitter() {}

    public record FrontendFile(String path, String content) {}

    public record ColSpec(
            String columnName,
            String label,
            String javaType,
            String javaField,
            String formType,
            boolean pk,
            boolean listShow,
            boolean formShow,
            boolean required) {}

    /** 表驱动前端生成参数 */
    public record TableFrontendParams(
            CodegenClientProfile profile,
            String title,
            String menuPath,
            String viewPath,
            String prefix,
            String pascal,
            String apiUrl,
            List<ColSpec> cols,
            ColSpec pk) {}

    /** 路由脚手架（标准 CRUD）前端生成参数 */
    public record RouteFrontendParams(
            CodegenClientProfile profile,
            String title,
            String routePath,
            String viewPath,
            String prefix,
            String pascal,
            String apiUrl) {}

    public static List<FrontendFile> emitTable(TableFrontendParams p) {
        return switch (p.profile().getStack()) {
            case VUE3 -> emitVue3Table(p);
            case VUE2 -> emitVue2Table(p);
            case REACT -> emitReactTable(p);
        };
    }

    public static List<FrontendFile> emitRouteCrud(RouteFrontendParams p) {
        return switch (p.profile().getStack()) {
            case VUE3 -> emitVue3Route(p);
            case VUE2 -> emitVue2Route(p);
            case REACT -> emitReactRoute(p);
        };
    }

    // ===================== Vue3 (ts / js) — table =====================

    private static List<FrontendFile> emitVue3Table(TableFrontendParams p) {
        boolean typed = p.profile().isTyped();
        List<FrontendFile> files = new ArrayList<>();
        files.add(
                new FrontendFile(
                        p.profile().pageFile(p.viewPath(), "index"), vue3IndexTable(p, typed)));
        files.add(
                new FrontendFile(
                        p.profile().pageFile(p.viewPath(), "save"), vue3SaveTable(p, typed)));
        files.add(
                new FrontendFile(
                        p.profile().apiFile(p.prefix()), vue3Api(p.apiUrl(), typed, false)));
        return files;
    }

    private static List<FrontendFile> emitVue3Route(RouteFrontendParams p) {
        boolean typed = p.profile().isTyped();
        List<FrontendFile> files = new ArrayList<>();
        files.add(
                new FrontendFile(
                        p.profile().pageFile(p.viewPath(), "index"), vue3IndexRoute(p, typed)));
        files.add(
                new FrontendFile(
                        p.profile().pageFile(p.viewPath(), "save"), vue3SaveRoute(p, typed)));
        files.add(
                new FrontendFile(
                        p.profile().apiFile(p.prefix()), vue3Api(p.apiUrl(), typed, true)));
        return files;
    }

    private static String vue3IndexTable(TableFrontendParams p, boolean typed) {
        StringBuilder columnDefs = new StringBuilder();
        columnDefs.append("  { type: 'selection', width: 50, fixed: true },\n");
        for (ColSpec c : p.cols()) {
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
                p.cols().stream()
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
                p.cols().stream()
                        .filter(
                                c ->
                                        c.javaField().equals("name")
                                                || c.columnName()
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains("name"))
                        .map(ColSpec::javaField)
                        .findFirst()
                        .orElse(p.pk().javaField());

        String tableKey = p.menuPath().replace('/', ':').replaceAll("^:", "");
        String pageName = CodegenNaming.toPascal(p.prefix()) + "Page";
        String pk = p.pk().javaField();
        String scriptOpen = typed ? "<script setup lang=\"ts\">" : "<script setup>";
        String typeImports =
                typed
                        ? """
                                import type { SearchForm } from '@/types/search'
                                import type { SaveMode } from '@/types/save'
                                import type { TableColumnItem } from '@/types/table'
                                """
                        : "";
        String saveRefDecl =
                typed
                        ? "const saveRef = ref<InstanceType<typeof SaveDialog>>()"
                        : "const saveRef = ref()";
        String tableDataDecl =
                typed ? "const tableData = ref<any[]>([])" : "const tableData = ref([])";
        String queryFormDecl =
                typed ? "const queryForm = ref<SearchForm>({})" : "const queryForm = ref({})";
        String viewModeDecl =
                typed
                        ? "const viewMode = ref<'table' | 'card'>('table')"
                        : "const viewMode = ref('table')";
        String selectedDecl =
                typed ? "const selected = ref<any[]>([])" : "const selected = ref([])";
        String columnsDecl = typed ? "const columns: TableColumnItem[] = [" : "const columns = [";
        String openSaveSig =
                typed
                        ? "function openSave(mode: SaveMode, id?: number)"
                        : "function openSave(mode, id)";
        String inquiresSig =
                typed
                        ? "async function inquires(form: SearchForm)"
                        : "async function inquires(form)";
        String handleDeleteSig =
                typed
                        ? "async function handleDelete(row: any)"
                        : "async function handleDelete(row)";
        String buttonClickSig =
                typed ? "function buttonClick(action: string)" : "function buttonClick(action)";
        String onTableActionSig =
                typed
                        ? "function onTableAction(payload: { action: string; row: Record<string, any> })"
                        : "function onTableAction(payload)";
        String selectionChange =
                typed
                        ? "@selection-change=\"(rows) => (selected = rows as any[])\""
                        : "@selection-change=\"(rows) => (selected = rows)\"";

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
                        %s
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

                %s
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
                %s
                defineOptions({ name: '%s' })

                const { searchItems, buttonItems, tableButtonItems } = usePageUi('%s')
                %s
                const loading = ref(false)
                %s
                const total = ref(0)
                const page = ref(1)
                const size = ref(10)
                %s
                %s
                %s

                %s
                %s
                ]

                %s {
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

                %s {
                  queryForm.value = form
                  page.value = 1
                  await loadData()
                }

                async function reset() {
                  queryForm.value = {}
                  page.value = 1
                  await loadData()
                }

                %s {
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

                %s {
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

                %s {
                  if (payload.action === 'edit') openSave('edit', payload.row.%s)
                  if (payload.action === 'delete') void handleDelete(payload.row)
                }

                onMounted(loadData)
                </script>
                """
                .formatted(
                        tableKey,
                        p.title(),
                        nameField,
                        selectionChange,
                        statusSlot,
                        scriptOpen,
                        p.prefix(),
                        typeImports,
                        pageName,
                        p.menuPath(),
                        saveRefDecl,
                        tableDataDecl,
                        queryFormDecl,
                        viewModeDecl,
                        selectedDecl,
                        columnsDecl,
                        columnDefs,
                        openSaveSig,
                        inquiresSig,
                        handleDeleteSig,
                        nameField,
                        pk,
                        pk,
                        pk,
                        buttonClickSig,
                        pk,
                        pk,
                        onTableActionSig,
                        pk);
    }

    private static String vue3SaveTable(TableFrontendParams p, boolean typed) {
        List<ColSpec> formCols = p.cols().stream().filter(ColSpec::formShow).toList();
        StringBuilder formItems = new StringBuilder();
        StringBuilder formInit = new StringBuilder();
        StringBuilder resetBody = new StringBuilder();
        StringBuilder loadBody = new StringBuilder();
        StringBuilder rules = new StringBuilder();

        for (ColSpec c : formCols) {
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

        String scriptOpen = typed ? "<script setup lang=\"ts\">" : "<script setup>";
        String typeImports =
                typed
                        ? """
                                import type { FormInstance, FormRules } from 'element-plus'
                                import { saveDialogTitle, type SaveMode } from '@/types/save'
                                """
                        : """
                                import { saveDialogTitle } from '@/types/save'
                                """;
        String formRefDecl =
                typed ? "const formRef = ref<FormInstance>()" : "const formRef = ref()";
        String modeDecl = typed ? "const mode = ref<SaveMode>('add')" : "const mode = ref('add')";
        String editingIdDecl =
                typed
                        ? "const editingId = ref<number | null>(null)"
                        : "const editingId = ref(null)";
        String rulesDecl = typed ? "const rules: FormRules = {" : "const rules = {";
        String openSig =
                typed
                        ? "async function open(openMode: SaveMode, id?: number)"
                        : "async function open(openMode, id)";
        String loadDetailSig =
                typed ? "async function loadDetail(id: number)" : "async function loadDetail(id)";
        String emitDecl =
                typed
                        ? "const emit = defineEmits<{ success: [] }>()"
                        : "const emit = defineEmits(['success'])";

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

                %s
                import { computed, reactive, ref } from 'vue'
                import { ElMessage } from 'element-plus'
                import { create, get, update } from '@/api/%s'
                %s
                defineOptions({ name: '%sSave' })
                %s
                const visible = ref(false)
                %s
                %s
                const submitting = ref(false)
                %s
                const dialogTitle = computed(() => saveDialogTitle(mode.value, '%s'))
                const form = reactive({
                %s})
                %s
                %s}

                function resetForm() {
                %s  editingId.value = null
                  formRef.value?.clearValidate()
                }

                %s {
                  const res = await get(id)
                %s}

                %s {
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
                .formatted(
                        formItems,
                        scriptOpen,
                        p.prefix(),
                        typeImports,
                        p.pascal(),
                        emitDecl,
                        modeDecl,
                        editingIdDecl,
                        formRefDecl,
                        p.title(),
                        formInit,
                        rulesDecl,
                        rules,
                        resetBody,
                        loadDetailSig,
                        loadBody,
                        openSig);
    }

    private static String vue3IndexRoute(RouteFrontendParams p, boolean typed) {
        String tableKey = p.routePath().replace('/', ':').replaceAll("^:", "");
        String pageName = CodegenNaming.toPascal(p.prefix()) + "Page";
        String scriptOpen = typed ? "<script setup lang=\"ts\">" : "<script setup>";
        String typeImports =
                typed
                        ? """
                                import type { SearchForm } from '@/types/search'
                                import type { SaveMode } from '@/types/save'
                                import type { TableColumnItem } from '@/types/table'
                                """
                        : "";
        String saveRefDecl =
                typed
                        ? "const saveRef = ref<InstanceType<typeof SaveDialog>>()"
                        : "const saveRef = ref()";
        String tableDataDecl =
                typed ? "const tableData = ref<any[]>([])" : "const tableData = ref([])";
        String queryFormDecl =
                typed ? "const queryForm = ref<SearchForm>({})" : "const queryForm = ref({})";
        String viewModeDecl =
                typed
                        ? "const viewMode = ref<'table' | 'card'>('table')"
                        : "const viewMode = ref('table')";
        String selectedDecl =
                typed ? "const selected = ref<any[]>([])" : "const selected = ref([])";
        String columnsDecl = typed ? "const columns: TableColumnItem[] = [" : "const columns = [";
        String openSaveSig =
                typed
                        ? "function openSave(mode: SaveMode, id?: number)"
                        : "function openSave(mode, id)";
        String inquiresSig =
                typed
                        ? "async function inquires(form: SearchForm)"
                        : "async function inquires(form)";
        String handleDeleteSig =
                typed
                        ? "async function handleDelete(row: any)"
                        : "async function handleDelete(row)";
        String buttonClickSig =
                typed ? "function buttonClick(action: string)" : "function buttonClick(action)";
        String onTableActionSig =
                typed
                        ? "function onTableAction(payload: { action: string; row: Record<string, any> })"
                        : "function onTableAction(payload)";
        String selectionChange =
                typed
                        ? "@selection-change=\"(rows) => (selected = rows as any[])\""
                        : "@selection-change=\"(rows) => (selected = rows)\"";

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
                        %s
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

                %s
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
                %s
                defineOptions({ name: '%s' })

                const { searchItems, buttonItems, tableButtonItems } = usePageUi('%s')
                %s
                const loading = ref(false)
                %s
                const total = ref(0)
                const page = ref(1)
                const size = ref(10)
                %s
                %s
                %s

                %s
                  { type: 'selection', width: 50, fixed: true },
                  { prop: 'id', label: 'ID', width: 80 },
                  { prop: 'code', label: '编码', minWidth: 120 },
                  { prop: 'name', label: '名称', minWidth: 160 },
                  { prop: 'sort', label: '排序', width: 90 },
                  { type: 'slot', slot: 'status', label: '状态', width: 90 },
                  { prop: 'remark', label: '备注', minWidth: 140, showOverflowTooltip: true },
                  { type: 'slot', slot: 'actions', label: '操作', fixed: 'right', width: 140 },
                ]

                %s {
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

                %s {
                  queryForm.value = form
                  page.value = 1
                  await loadData()
                }

                async function reset() {
                  queryForm.value = {}
                  page.value = 1
                  await loadData()
                }

                %s {
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

                %s {
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

                %s {
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
                        tableKey,
                        p.title(),
                        selectionChange,
                        scriptOpen,
                        p.prefix(),
                        typeImports,
                        pageName,
                        p.routePath(),
                        saveRefDecl,
                        tableDataDecl,
                        queryFormDecl,
                        viewModeDecl,
                        selectedDecl,
                        columnsDecl,
                        openSaveSig,
                        inquiresSig,
                        handleDeleteSig,
                        buttonClickSig,
                        onTableActionSig);
    }

    private static String vue3SaveRoute(RouteFrontendParams p, boolean typed) {
        String scriptOpen = typed ? "<script setup lang=\"ts\">" : "<script setup>";
        String typeImports =
                typed
                        ? """
                                import type { FormInstance, FormRules } from 'element-plus'
                                import { saveDialogTitle, type SaveMode } from '@/types/save'
                                """
                        : """
                                import { saveDialogTitle } from '@/types/save'
                                """;
        String formRefDecl =
                typed ? "const formRef = ref<FormInstance>()" : "const formRef = ref()";
        String modeDecl = typed ? "const mode = ref<SaveMode>('add')" : "const mode = ref('add')";
        String editingIdDecl =
                typed
                        ? "const editingId = ref<number | null>(null)"
                        : "const editingId = ref(null)";
        String statusInit = typed ? "status: 1 as number," : "status: 1,";
        String rulesDecl = typed ? "const rules: FormRules = {" : "const rules = {";
        String openSig =
                typed
                        ? "async function open(openMode: SaveMode, id?: number)"
                        : "async function open(openMode, id)";
        String loadDetailSig =
                typed ? "async function loadDetail(id: number)" : "async function loadDetail(id)";
        String emitDecl =
                typed
                        ? "const emit = defineEmits<{ success: [] }>()"
                        : "const emit = defineEmits(['success'])";

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

                %s
                import { computed, reactive, ref } from 'vue'
                import { ElMessage } from 'element-plus'
                import { create, get, update } from '@/api/%s'
                %s
                defineOptions({ name: '%sSave' })
                %s
                const visible = ref(false)
                %s
                %s
                const submitting = ref(false)
                %s
                const dialogTitle = computed(() => saveDialogTitle(mode.value, '%s'))
                const form = reactive({
                  code: '',
                  name: '',
                  sort: 0,
                  %s
                  remark: '',
                })
                %s
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

                %s {
                  const res = await get(id)
                  form.code = res.data.code ?? ''
                  form.name = res.data.name ?? ''
                  form.sort = res.data.sort ?? 0
                  form.status = res.data.status ?? 1
                  form.remark = res.data.remark ?? ''
                }

                %s {
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
                .formatted(
                        scriptOpen,
                        p.prefix(),
                        typeImports,
                        p.pascal(),
                        emitDecl,
                        modeDecl,
                        editingIdDecl,
                        formRefDecl,
                        p.title(),
                        statusInit,
                        rulesDecl,
                        loadDetailSig,
                        openSig);
    }

    private static String vue3Api(String apiUrl, boolean typed, boolean withStatus) {
        if (typed) {
            String listParams =
                    withStatus
                            ? "export type ListParams = { page: number; size: number; keyword?: string; status?: number }"
                            : "export type ListParams = { page: number; size: number; keyword?: string }";
            return """
                    import request from '@/utils/request'
                    import type { ApiResponse, PageResult } from '@/types'

                    %s

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
                    .formatted(listParams, apiUrl, apiUrl, apiUrl, apiUrl, apiUrl, apiUrl);
        }
        return """
                import request from '@/utils/request'

                export function list(params) {
                  return request.get('%s', { params })
                }

                export function get(id) {
                  return request.get(`%s/${id}`)
                }

                export function create(data) {
                  return request.post('%s', data)
                }

                export function update(id, data) {
                  return request.put(`%s/${id}`, data)
                }

                export function remove(id) {
                  return request.delete(`%s/${id}`)
                }

                export function batchRemove(ids) {
                  return request.post('%s/batch-delete', { ids })
                }
                """
                .formatted(apiUrl, apiUrl, apiUrl, apiUrl, apiUrl, apiUrl);
    }

    // ===================== Vue2 Options API =====================

    private static List<FrontendFile> emitVue2Table(TableFrontendParams p) {
        List<FrontendFile> files = new ArrayList<>();
        files.add(new FrontendFile(p.profile().pageFile(p.viewPath(), "index"), vue2IndexTable(p)));
        files.add(new FrontendFile(p.profile().pageFile(p.viewPath(), "save"), vue2SaveTable(p)));
        files.add(
                new FrontendFile(
                        p.profile().apiFile(p.prefix()), vue3Api(p.apiUrl(), false, false)));
        return files;
    }

    private static List<FrontendFile> emitVue2Route(RouteFrontendParams p) {
        List<FrontendFile> files = new ArrayList<>();
        files.add(new FrontendFile(p.profile().pageFile(p.viewPath(), "index"), vue2IndexRoute(p)));
        files.add(new FrontendFile(p.profile().pageFile(p.viewPath(), "save"), vue2SaveRoute(p)));
        files.add(
                new FrontendFile(
                        p.profile().apiFile(p.prefix()), vue3Api(p.apiUrl(), false, true)));
        return files;
    }

    private static String vue2IndexTable(TableFrontendParams p) {
        StringBuilder columnDefs = new StringBuilder();
        columnDefs.append("  { type: 'selection', width: 50, fixed: true },\n");
        for (ColSpec c : p.cols()) {
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
                p.cols().stream()
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
                p.cols().stream()
                        .filter(
                                c ->
                                        c.javaField().equals("name")
                                                || c.columnName()
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains("name"))
                        .map(ColSpec::javaField)
                        .findFirst()
                        .orElse(p.pk().javaField());
        String tableKey = p.menuPath().replace('/', ':').replaceAll("^:", "");
        String pageName = CodegenNaming.toPascal(p.prefix()) + "Page";
        String pk = p.pk().javaField();

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
                        @selection-change="selectionChangeHandle"
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

                <script>
                import { ElMessage, ElMessageBox } from 'element-plus'
                import xnPageLayout from '@/components/xnPageLayout/xnPageLayout.vue'
                import xnSearch from '@/components/xnSearch/xnSearch.vue'
                import xnButton from '@/components/xnButton/xnButton.vue'
                import xnTableActions from '@/components/xnButton/xnTableActions.vue'
                import xnTable from '@/components/xnTable/xnTable.vue'
                import SaveDialog from './save.vue'
                import { usePageUi } from '@/composables/usePageUi'
                import { list, batchRemove, remove } from '@/api/%s'

                const columns = [
                %s
                ]

                export default {
                  name: '%s',
                  components: {
                    xnPageLayout,
                    xnSearch,
                    xnButton,
                    xnTableActions,
                    xnTable,
                    SaveDialog,
                  },
                  setup() {
                    const { searchItems, buttonItems, tableButtonItems } = usePageUi('%s')
                    return { searchItems, buttonItems, tableButtonItems }
                  },
                  data() {
                    return {
                      loading: false,
                      tableData: [],
                      total: 0,
                      page: 1,
                      size: 10,
                      queryForm: {},
                      viewMode: 'table',
                      selected: [],
                      columns,
                    }
                  },
                  mounted() {
                    this.loadData()
                  },
                  methods: {
                    openSave(mode, id) {
                      this.$refs.saveRef?.open(mode, id)
                    },
                    selectionChangeHandle(rows) {
                      this.selected = rows
                    },
                    async loadData() {
                      this.loading = true
                      try {
                        const res = await list({
                          page: this.page - 1,
                          size: this.size,
                          keyword: String(this.queryForm.FuzzyWord ?? '').trim() || undefined,
                        })
                        this.tableData = res.data.records
                        this.total = res.data.total
                      } finally {
                        this.loading = false
                      }
                    },
                    async inquires(form) {
                      this.queryForm = form
                      this.page = 1
                      await this.loadData()
                    },
                    async reset() {
                      this.queryForm = {}
                      this.page = 1
                      await this.loadData()
                    },
                    async handleDelete(row) {
                      await ElMessageBox.confirm(`确认删除「${row.%s ?? row.%s}」？`, '提示', { type: 'warning' })
                      await remove(row.%s)
                      ElMessage.success('删除成功')
                      await this.loadData()
                    },
                    async handleBatchDelete() {
                      if (!this.selected.length) {
                        ElMessage.warning('请先选择数据')
                        return
                      }
                      await ElMessageBox.confirm(`确认删除选中的 ${this.selected.length} 条？`, '提示', { type: 'warning' })
                      await batchRemove(this.selected.map((r) => r.%s))
                      ElMessage.success('删除成功')
                      await this.loadData()
                    },
                    buttonClick(action) {
                      if (action === 'add') this.openSave('add')
                      else if (action === 'edit') {
                        if (this.selected.length !== 1) {
                          ElMessage.warning('请选择一条记录')
                          return
                        }
                        this.openSave('edit', this.selected[0].%s)
                      } else if (action === 'view') {
                        if (this.selected.length !== 1) {
                          ElMessage.warning('请选择一条记录')
                          return
                        }
                        this.openSave('view', this.selected[0].%s)
                      } else if (action === 'delete') this.handleBatchDelete()
                    },
                    onTableAction(payload) {
                      if (payload.action === 'edit') this.openSave('edit', payload.row.%s)
                      else if (payload.action === 'delete') this.handleDelete(payload.row)
                    },
                  },
                }
                </script>
                """
                .formatted(
                        tableKey,
                        p.title(),
                        nameField,
                        statusSlot,
                        p.prefix(),
                        columnDefs,
                        pageName,
                        p.menuPath(),
                        nameField,
                        pk,
                        pk,
                        pk,
                        pk,
                        pk,
                        pk);
    }

    private static String vue2SaveTable(TableFrontendParams p) {
        List<ColSpec> formCols = p.cols().stream().filter(ColSpec::formShow).toList();
        StringBuilder formItems = new StringBuilder();
        StringBuilder formInit = new StringBuilder();
        StringBuilder resetBody = new StringBuilder();
        StringBuilder loadBody = new StringBuilder();
        StringBuilder rules = new StringBuilder();

        for (ColSpec c : formCols) {
            formInit.append("        ")
                    .append(c.javaField())
                    .append(": ")
                    .append(defaultJsValue(c))
                    .append(",\n");
            resetBody
                    .append("      this.form.")
                    .append(c.javaField())
                    .append(" = ")
                    .append(defaultJsValue(c))
                    .append("\n");
            loadBody.append("      this.form.")
                    .append(c.javaField())
                    .append(" = res.data.")
                    .append(c.javaField())
                    .append(" ?? ")
                    .append(defaultJsValue(c))
                    .append("\n");
            if (c.required() && "String".equals(c.javaType())) {
                rules.append("        ")
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

                <script>
                import { ElMessage } from 'element-plus'
                import { create, get, update } from '@/api/%s'
                import { saveDialogTitle } from '@/types/save'

                export default {
                  name: '%sSave',
                  emits: ['success'],
                  data() {
                    return {
                      visible: false,
                      mode: 'add',
                      editingId: null,
                      submitting: false,
                      form: {
                %s      },
                      rules: {
                %s      },
                    }
                  },
                  computed: {
                    dialogTitle() {
                      return saveDialogTitle(this.mode, '%s')
                    },
                  },
                  methods: {
                    resetForm() {
                %s      this.editingId = null
                      this.$refs.formRef?.clearValidate()
                    },
                    async loadDetail(id) {
                      const res = await get(id)
                %s    },
                    async open(openMode, id) {
                      this.mode = openMode
                      this.resetForm()
                      this.editingId = id ?? null
                      this.visible = true
                      if (openMode !== 'add' && id) await this.loadDetail(id)
                    },
                    async handleSubmit() {
                      const formRef = this.$refs.formRef
                      if (!formRef) return
                      await formRef.validate(async (valid) => {
                        if (!valid) return
                        this.submitting = true
                        try {
                          if (this.mode === 'edit' && this.editingId) {
                            await update(this.editingId, { ...this.form })
                            ElMessage.success('更新成功')
                          } else {
                            await create({ ...this.form })
                            ElMessage.success('创建成功')
                          }
                          this.visible = false
                          this.$emit('success')
                        } finally {
                          this.submitting = false
                        }
                      })
                    },
                    handleClosed() {
                      this.resetForm()
                    },
                  },
                }
                </script>
                """
                .formatted(
                        formItems,
                        p.prefix(),
                        p.pascal(),
                        formInit,
                        rules,
                        p.title(),
                        resetBody,
                        loadBody);
    }

    private static String vue2IndexRoute(RouteFrontendParams p) {
        String tableKey = p.routePath().replace('/', ':').replaceAll("^:", "");
        String pageName = CodegenNaming.toPascal(p.prefix()) + "Page";
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
                        @selection-change="selectionChangeHandle"
                        @page-change="loadData"
                      >
                        <template #status="{ row }">
                          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                            {{ row.status === 1 ? '启用' : '停用' }}
                          </el-tag>
                        </template>
                        <template #actions="{ row }">
                          <xnTableActions :items="tableButtonItems" :row="row" @action-click="onTableAction" />
                        </template>
                      </xnTable>
                    </template>
                  </xnPageLayout>
                  <SaveDialog ref="saveRef" @success="loadData" />
                </template>

                <script>
                import { ElMessage, ElMessageBox } from 'element-plus'
                import xnPageLayout from '@/components/xnPageLayout/xnPageLayout.vue'
                import xnSearch from '@/components/xnSearch/xnSearch.vue'
                import xnButton from '@/components/xnButton/xnButton.vue'
                import xnTableActions from '@/components/xnButton/xnTableActions.vue'
                import xnTable from '@/components/xnTable/xnTable.vue'
                import SaveDialog from './save.vue'
                import { usePageUi } from '@/composables/usePageUi'
                import { list, batchRemove, remove } from '@/api/%s'

                const columns = [
                  { type: 'selection', width: 50, fixed: true },
                  { prop: 'id', label: 'ID', width: 80 },
                  { prop: 'code', label: '编码', minWidth: 120 },
                  { prop: 'name', label: '名称', minWidth: 160 },
                  { prop: 'sort', label: '排序', width: 90 },
                  { type: 'slot', slot: 'status', label: '状态', width: 90 },
                  { prop: 'remark', label: '备注', minWidth: 140, showOverflowTooltip: true },
                  { type: 'slot', slot: 'actions', label: '操作', fixed: 'right', width: 140 },
                ]

                export default {
                  name: '%s',
                  components: {
                    xnPageLayout,
                    xnSearch,
                    xnButton,
                    xnTableActions,
                    xnTable,
                    SaveDialog,
                  },
                  setup() {
                    const { searchItems, buttonItems, tableButtonItems } = usePageUi('%s')
                    return { searchItems, buttonItems, tableButtonItems }
                  },
                  data() {
                    return {
                      loading: false,
                      tableData: [],
                      total: 0,
                      page: 1,
                      size: 10,
                      queryForm: {},
                      viewMode: 'table',
                      selected: [],
                      columns,
                    }
                  },
                  mounted() {
                    this.loadData()
                  },
                  methods: {
                    openSave(mode, id) {
                      this.$refs.saveRef?.open(mode, id)
                    },
                    selectionChangeHandle(rows) {
                      this.selected = rows
                    },
                    async loadData() {
                      this.loading = true
                      try {
                        const res = await list({
                          page: this.page - 1,
                          size: this.size,
                          keyword: String(this.queryForm.FuzzyWord ?? '').trim() || undefined,
                        })
                        this.tableData = res.data.records
                        this.total = res.data.total
                      } finally {
                        this.loading = false
                      }
                    },
                    async inquires(form) {
                      this.queryForm = form
                      this.page = 1
                      await this.loadData()
                    },
                    async reset() {
                      this.queryForm = {}
                      this.page = 1
                      await this.loadData()
                    },
                    async handleDelete(row) {
                      await ElMessageBox.confirm(`确认删除「${row.name ?? row.id}」？`, '提示', { type: 'warning' })
                      await remove(row.id)
                      ElMessage.success('删除成功')
                      await this.loadData()
                    },
                    async handleBatchDelete() {
                      if (!this.selected.length) {
                        ElMessage.warning('请先选择数据')
                        return
                      }
                      await ElMessageBox.confirm(`确认删除选中的 ${this.selected.length} 条？`, '提示', { type: 'warning' })
                      await batchRemove(this.selected.map((r) => r.id))
                      ElMessage.success('删除成功')
                      await this.loadData()
                    },
                    buttonClick(action) {
                      if (action === 'add') this.openSave('add')
                      else if (action === 'edit') {
                        if (this.selected.length !== 1) {
                          ElMessage.warning('请选择一条记录')
                          return
                        }
                        this.openSave('edit', this.selected[0].id)
                      } else if (action === 'view') {
                        if (this.selected.length !== 1) {
                          ElMessage.warning('请选择一条记录')
                          return
                        }
                        this.openSave('view', this.selected[0].id)
                      } else if (action === 'delete') this.handleBatchDelete()
                    },
                    onTableAction(payload) {
                      if (payload.action === 'edit') this.openSave('edit', payload.row.id)
                      else if (payload.action === 'delete') this.handleDelete(payload.row)
                    },
                  },
                }
                </script>
                """
                .formatted(tableKey, p.title(), p.prefix(), pageName, p.routePath());
    }

    private static String vue2SaveRoute(RouteFrontendParams p) {
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

                <script>
                import { ElMessage } from 'element-plus'
                import { create, get, update } from '@/api/%s'
                import { saveDialogTitle } from '@/types/save'

                export default {
                  name: '%sSave',
                  emits: ['success'],
                  data() {
                    return {
                      visible: false,
                      mode: 'add',
                      editingId: null,
                      submitting: false,
                      form: {
                        code: '',
                        name: '',
                        sort: 0,
                        status: 1,
                        remark: '',
                      },
                      rules: {
                        name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
                        code: [
                          { required: true, message: '请输入编码', trigger: 'blur' },
                          {
                            pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/,
                            message: '需以字母开头，只能包含字母、数字、下划线',
                            trigger: 'blur',
                          },
                        ],
                      },
                    }
                  },
                  computed: {
                    dialogTitle() {
                      return saveDialogTitle(this.mode, '%s')
                    },
                  },
                  methods: {
                    resetForm() {
                      this.form = { code: '', name: '', sort: 0, status: 1, remark: '' }
                      this.editingId = null
                      this.$refs.formRef?.clearValidate()
                    },
                    async loadDetail(id) {
                      const res = await get(id)
                      this.form.code = res.data.code ?? ''
                      this.form.name = res.data.name ?? ''
                      this.form.sort = res.data.sort ?? 0
                      this.form.status = res.data.status ?? 1
                      this.form.remark = res.data.remark ?? ''
                    },
                    async open(openMode, id) {
                      this.mode = openMode
                      this.resetForm()
                      this.editingId = id ?? null
                      this.visible = true
                      if (openMode !== 'add' && id) await this.loadDetail(id)
                    },
                    async handleSubmit() {
                      const formRef = this.$refs.formRef
                      if (!formRef) return
                      await formRef.validate(async (valid) => {
                        if (!valid) return
                        this.submitting = true
                        try {
                          if (this.mode === 'edit' && this.editingId) {
                            await update(this.editingId, { ...this.form })
                            ElMessage.success('更新成功')
                          } else {
                            await create({ ...this.form })
                            ElMessage.success('创建成功')
                          }
                          this.visible = false
                          this.$emit('success')
                        } finally {
                          this.submitting = false
                        }
                      })
                    },
                    handleClosed() {
                      this.resetForm()
                    },
                  },
                }
                </script>
                """
                .formatted(p.prefix(), p.pascal(), p.title());
    }

    // ===================== React + Ant Design =====================

    private static List<FrontendFile> emitReactTable(TableFrontendParams p) {
        List<FrontendFile> files = new ArrayList<>();
        files.add(
                new FrontendFile(p.profile().pageFile(p.viewPath(), "index"), reactIndexTable(p)));
        files.add(new FrontendFile(p.profile().pageFile(p.viewPath(), "save"), reactSaveTable(p)));
        files.add(new FrontendFile(p.profile().apiFile(p.prefix()), reactApi(p.apiUrl(), false)));
        return files;
    }

    private static List<FrontendFile> emitReactRoute(RouteFrontendParams p) {
        List<FrontendFile> files = new ArrayList<>();
        files.add(
                new FrontendFile(p.profile().pageFile(p.viewPath(), "index"), reactIndexRoute(p)));
        files.add(new FrontendFile(p.profile().pageFile(p.viewPath(), "save"), reactSaveRoute(p)));
        files.add(new FrontendFile(p.profile().apiFile(p.prefix()), reactApi(p.apiUrl(), true)));
        return files;
    }

    private static String reactIndexTable(TableFrontendParams p) {
        StringBuilder columnDefs = new StringBuilder();
        columnDefs.append("    { type: 'selection', width: 50 },\n");
        for (ColSpec c : p.cols()) {
            if (!c.listShow()) continue;
            if ("status".equalsIgnoreCase(c.columnName()) && "select".equals(c.formType())) {
                columnDefs
                        .append("    {\n")
                        .append("      type: 'tag',\n")
                        .append("      prop: '")
                        .append(c.javaField())
                        .append("',\n")
                        .append("      label: '")
                        .append(escapeJs(c.label()))
                        .append("',\n")
                        .append("      width: 100,\n")
                        .append(
                                "      options: [\n        { value: 1, label: '启用', type: 'success' },\n")
                        .append("        { value: 0, label: '停用', type: 'info' },\n      ],\n")
                        .append("    },\n");
            } else {
                columnDefs
                        .append("    { prop: '")
                        .append(c.javaField())
                        .append("', label: '")
                        .append(escapeJs(c.label()))
                        .append("', minWidth: 120")
                        .append(c.pk() ? ", width: 80" : "")
                        .append(" },\n");
            }
        }
        columnDefs.append(
                "    { type: 'slot', slot: 'actions', label: '操作', fixed: 'right', width: 160 },");

        String nameField =
                p.cols().stream()
                        .filter(
                                c ->
                                        c.javaField().equals("name")
                                                || c.columnName()
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains("name"))
                        .map(ColSpec::javaField)
                        .findFirst()
                        .orElse(p.pk().javaField());
        String tableKey = p.menuPath().replace('/', ':').replaceAll("^:", "");
        String saveName = p.pascal() + "Save";
        String pk = p.pk().javaField();

        return """
                import { useEffect, useRef, useState } from 'react'
                import { message, Modal } from 'antd'
                import XnPageLayout from '@/components/XnPageLayout'
                import XnSearch from '@/components/XnSearch'
                import XnButton, { XnTableActions } from '@/components/XnButton'
                import XnTable from '@/components/XnTable'
                import %s, { type %sHandle } from './save'
                import { usePageUi } from '@/hooks/usePageUi'
                import { list, remove, batchRemove } from '@/api/%s'
                import type { SearchForm } from '@/types/search'
                import type { SaveMode } from '@/types/save'
                import type { TableColumnItem } from '@/types/table'

                export default function %sPage() {
                  const { searchItems, buttonItems, tableButtonItems } = usePageUi('%s')
                  const saveRef = useRef<%sHandle>(null)
                  const [loading, setLoading] = useState(false)
                  const [tableData, setTableData] = useState<any[]>([])
                  const [total, setTotal] = useState(0)
                  const [page, setPage] = useState(1)
                  const [size, setSize] = useState(10)
                  const [queryForm, setQueryForm] = useState<SearchForm>({})
                  const [viewMode, setViewMode] = useState<'table' | 'card'>('table')
                  const [selected, setSelected] = useState<any[]>([])

                  const columns: TableColumnItem[] = [
                %s
                  ]

                  async function loadData(nextPage = page, nextSize = size, nextQuery = queryForm) {
                    setLoading(true)
                    try {
                      const res = await list({
                        page: nextPage - 1,
                        size: nextSize,
                        keyword: String(nextQuery.FuzzyWord ?? '').trim() || undefined,
                      })
                      setTableData(res.data.records)
                      setTotal(res.data.total)
                    } finally {
                      setLoading(false)
                    }
                  }

                  useEffect(() => {
                    void loadData()
                    // eslint-disable-next-line react-hooks/exhaustive-deps
                  }, [])

                  function openSave(mode: SaveMode, id?: number) {
                    void saveRef.current?.open(mode, id)
                  }

                  async function handleDelete(row: any) {
                    Modal.confirm({
                      title: '确认删除',
                      content: `确定删除「${row.%s ?? row.%s}」吗？`,
                      okType: 'danger',
                      onOk: async () => {
                        await remove(row.%s)
                        message.success('删除成功')
                        await loadData()
                      },
                    })
                  }

                  async function handleBatchDelete() {
                    if (!selected.length) {
                      message.warning('请选择要删除的数据')
                      return
                    }
                    Modal.confirm({
                      title: '确认删除',
                      content: `确定删除选中的 ${selected.length} 条吗？`,
                      okType: 'danger',
                      onOk: async () => {
                        await batchRemove(selected.map((r) => r.%s))
                        message.success('删除成功')
                        setSelected([])
                        await loadData()
                      },
                    })
                  }

                  function buttonClick(action: string) {
                    if (action === 'add') {
                      openSave('add')
                      return
                    }
                    if (action === 'edit' || action === 'view') {
                      if (selected.length !== 1) {
                        message.warning('请选择一项操作')
                        return
                      }
                      openSave(action, selected[0].%s)
                      return
                    }
                    if (action === 'delete') void handleBatchDelete()
                  }

                  return (
                    <>
                      <XnPageLayout
                        viewMode={viewMode}
                        onViewModeChange={setViewMode}
                        showPagination={viewMode === 'card'}
                        page={page}
                        pageSize={size}
                        total={total}
                        loading={viewMode === 'card' ? loading : false}
                        onPageChange={(p, s) => {
                          setPage(p)
                          setSize(s)
                          void loadData(p, s)
                        }}
                        search={
                          <XnSearch
                            searchItem={searchItems}
                            onQueryForm={(form) => {
                              setQueryForm(form)
                              setPage(1)
                              void loadData(1, size, form)
                            }}
                            onReset={(form) => {
                              setQueryForm(form)
                              setPage(1)
                              void loadData(1, size, form)
                            }}
                          />
                        }
                        toolbar={
                          <XnButton listItem={buttonItems} selected={selected} onButtonClick={buttonClick} />
                        }
                        table={
                          <XnTable
                            data={tableData}
                            total={total}
                            loading={loading}
                            page={page}
                            pageSize={size}
                            tableKey="%s"
                            entityName="%s"
                            nameField="%s"
                            columns={columns}
                            actionItems={tableButtonItems}
                            onSelectionChange={(rows) => setSelected(rows)}
                            onPageChange={(p, s) => {
                              setPage(p)
                              setSize(s)
                              void loadData(p, s)
                            }}
                            onRefresh={() => void loadData()}
                            slots={{
                              actions: ({ row }) => (
                                <XnTableActions
                                  items={tableButtonItems}
                                  row={row}
                                  onActionClick={({ action, row: r }) => {
                                    if (action === 'delete') void handleDelete(r)
                                    else if (action === 'edit' || action === 'view') openSave(action, (r as any).%s)
                                  }}
                                />
                              ),
                            }}
                          />
                        }
                      />
                      <%s ref={saveRef} onSuccess={() => void loadData()} />
                    </>
                  )
                }
                """
                .formatted(
                        saveName,
                        saveName,
                        p.prefix(),
                        p.pascal(),
                        p.menuPath(),
                        saveName,
                        columnDefs,
                        nameField,
                        pk,
                        pk,
                        pk,
                        pk,
                        tableKey,
                        p.title(),
                        nameField,
                        pk,
                        saveName);
    }

    private static String reactSaveTable(TableFrontendParams p) {
        List<ColSpec> formCols = p.cols().stream().filter(ColSpec::formShow).toList();
        StringBuilder formItems = new StringBuilder();
        StringBuilder defaults = new StringBuilder();
        for (ColSpec c : formCols) {
            defaults.append("          ")
                    .append(c.javaField())
                    .append(": ")
                    .append(defaultJsValue(c))
                    .append(",\n");
            formItems.append(formItemReact(c));
        }
        String saveName = p.pascal() + "Save";
        String handleName = saveName + "Handle";

        return """
                import { forwardRef, useImperativeHandle, useState } from 'react'
                import { Form, Input, InputNumber, Radio, Switch, message } from 'antd'
                import XnModal from '@/components/XnModal'
                import { create, get, update } from '@/api/%s'
                import { saveDialogTitle, type SaveMode } from '@/types/save'

                export interface %s {
                  open: (mode: SaveMode, id?: number) => Promise<void>
                }

                const %s = forwardRef<%s, { onSuccess?: () => void }>(function %s(
                  { onSuccess },
                  ref,
                ) {
                  const [visible, setVisible] = useState(false)
                  const [mode, setMode] = useState<SaveMode>('add')
                  const [editingId, setEditingId] = useState<number | null>(null)
                  const [submitting, setSubmitting] = useState(false)
                  const [form] = Form.useForm()

                  useImperativeHandle(ref, () => ({
                    async open(openMode, id) {
                      setMode(openMode)
                      setEditingId(id ?? null)
                      form.resetFields()
                      form.setFieldsValue({
                %s      })
                      setVisible(true)
                      if (openMode !== 'add' && id) {
                        const res = await get(id)
                        form.setFieldsValue(res.data)
                      }
                    },
                  }))

                  async function handleSubmit() {
                    const values = await form.validateFields()
                    setSubmitting(true)
                    try {
                      if (mode === 'edit' && editingId) {
                        await update(editingId, values)
                        message.success('更新成功')
                      } else {
                        await create(values)
                        message.success('创建成功')
                      }
                      setVisible(false)
                      onSuccess?.()
                    } finally {
                      setSubmitting(false)
                    }
                  }

                  return (
                    <XnModal
                      title={saveDialogTitle(mode, '%s')}
                      open={visible}
                      onCancel={() => setVisible(false)}
                      destroyOnHidden
                      width={560}
                      okText="保存"
                      cancelText={mode === 'view' ? '关闭' : '取消'}
                      okButtonProps={{ style: mode === 'view' ? { display: 'none' } : undefined }}
                      confirmLoading={submitting}
                      onOk={() => void handleSubmit()}
                    >
                      <Form form={form} labelCol={{ span: 5 }} disabled={mode === 'view'}>
                %s      </Form>
                    </XnModal>
                  )
                })

                export default %s
                """
                .formatted(
                        p.prefix(),
                        handleName,
                        saveName,
                        handleName,
                        saveName,
                        defaults,
                        p.title(),
                        formItems,
                        saveName);
    }

    private static String reactIndexRoute(RouteFrontendParams p) {
        String tableKey = p.routePath().replace('/', ':').replaceAll("^:", "");
        String saveName = p.pascal() + "Save";
        return """
                import { useEffect, useRef, useState } from 'react'
                import { message, Modal } from 'antd'
                import XnPageLayout from '@/components/XnPageLayout'
                import XnSearch from '@/components/XnSearch'
                import XnButton, { XnTableActions } from '@/components/XnButton'
                import XnTable from '@/components/XnTable'
                import %s, { type %sHandle } from './save'
                import { usePageUi } from '@/hooks/usePageUi'
                import { list, remove, batchRemove } from '@/api/%s'
                import type { SearchForm } from '@/types/search'
                import type { SaveMode } from '@/types/save'
                import type { TableColumnItem } from '@/types/table'

                export default function %sPage() {
                  const { searchItems, buttonItems, tableButtonItems } = usePageUi('%s')
                  const saveRef = useRef<%sHandle>(null)
                  const [loading, setLoading] = useState(false)
                  const [tableData, setTableData] = useState<any[]>([])
                  const [total, setTotal] = useState(0)
                  const [page, setPage] = useState(1)
                  const [size, setSize] = useState(10)
                  const [queryForm, setQueryForm] = useState<SearchForm>({})
                  const [viewMode, setViewMode] = useState<'table' | 'card'>('table')
                  const [selected, setSelected] = useState<any[]>([])

                  const columns: TableColumnItem[] = [
                    { type: 'selection', width: 50 },
                    { prop: 'id', label: 'ID', width: 80 },
                    { prop: 'code', label: '编码', minWidth: 120 },
                    { prop: 'name', label: '名称', minWidth: 160 },
                    { prop: 'sort', label: '排序', width: 90 },
                    {
                      type: 'tag',
                      prop: 'status',
                      label: '状态',
                      width: 100,
                      options: [
                        { value: 1, label: '启用', type: 'success' },
                        { value: 0, label: '停用', type: 'info' },
                      ],
                    },
                    { prop: 'remark', label: '备注', minWidth: 140, showOverflowTooltip: true },
                    { type: 'slot', slot: 'actions', label: '操作', fixed: 'right', width: 160 },
                  ]

                  async function loadData(nextPage = page, nextSize = size, nextQuery = queryForm) {
                    setLoading(true)
                    try {
                      const res = await list({
                        page: nextPage - 1,
                        size: nextSize,
                        keyword: String(nextQuery.FuzzyWord ?? '').trim() || undefined,
                      })
                      setTableData(res.data.records)
                      setTotal(res.data.total)
                    } finally {
                      setLoading(false)
                    }
                  }

                  useEffect(() => {
                    void loadData()
                    // eslint-disable-next-line react-hooks/exhaustive-deps
                  }, [])

                  function openSave(mode: SaveMode, id?: number) {
                    void saveRef.current?.open(mode, id)
                  }

                  async function handleDelete(row: any) {
                    Modal.confirm({
                      title: '确认删除',
                      content: `确定删除「${row.name ?? row.id}」吗？`,
                      okType: 'danger',
                      onOk: async () => {
                        await remove(row.id)
                        message.success('删除成功')
                        await loadData()
                      },
                    })
                  }

                  async function handleBatchDelete() {
                    if (!selected.length) {
                      message.warning('请选择要删除的数据')
                      return
                    }
                    Modal.confirm({
                      title: '确认删除',
                      content: `确定删除选中的 ${selected.length} 条吗？`,
                      okType: 'danger',
                      onOk: async () => {
                        await batchRemove(selected.map((r) => r.id))
                        message.success('删除成功')
                        setSelected([])
                        await loadData()
                      },
                    })
                  }

                  function buttonClick(action: string) {
                    if (action === 'add') {
                      openSave('add')
                      return
                    }
                    if (action === 'edit' || action === 'view') {
                      if (selected.length !== 1) {
                        message.warning('请选择一项操作')
                        return
                      }
                      openSave(action, selected[0].id)
                      return
                    }
                    if (action === 'delete') void handleBatchDelete()
                  }

                  return (
                    <>
                      <XnPageLayout
                        viewMode={viewMode}
                        onViewModeChange={setViewMode}
                        showPagination={viewMode === 'card'}
                        page={page}
                        pageSize={size}
                        total={total}
                        loading={viewMode === 'card' ? loading : false}
                        onPageChange={(p, s) => {
                          setPage(p)
                          setSize(s)
                          void loadData(p, s)
                        }}
                        search={
                          <XnSearch
                            searchItem={searchItems}
                            onQueryForm={(form) => {
                              setQueryForm(form)
                              setPage(1)
                              void loadData(1, size, form)
                            }}
                            onReset={(form) => {
                              setQueryForm(form)
                              setPage(1)
                              void loadData(1, size, form)
                            }}
                          />
                        }
                        toolbar={
                          <XnButton listItem={buttonItems} selected={selected} onButtonClick={buttonClick} />
                        }
                        table={
                          <XnTable
                            data={tableData}
                            total={total}
                            loading={loading}
                            page={page}
                            pageSize={size}
                            tableKey="%s"
                            entityName="%s"
                            nameField="name"
                            columns={columns}
                            actionItems={tableButtonItems}
                            onSelectionChange={(rows) => setSelected(rows)}
                            onPageChange={(p, s) => {
                              setPage(p)
                              setSize(s)
                              void loadData(p, s)
                            }}
                            onRefresh={() => void loadData()}
                            slots={{
                              actions: ({ row }) => (
                                <XnTableActions
                                  items={tableButtonItems}
                                  row={row}
                                  onActionClick={({ action, row: r }) => {
                                    if (action === 'delete') void handleDelete(r)
                                    else if (action === 'edit' || action === 'view')
                                      openSave(action, (r as any).id)
                                  }}
                                />
                              ),
                            }}
                          />
                        }
                      />
                      <%s ref={saveRef} onSuccess={() => void loadData()} />
                    </>
                  )
                }
                """
                .formatted(
                        saveName,
                        saveName,
                        p.prefix(),
                        p.pascal(),
                        p.routePath(),
                        saveName,
                        tableKey,
                        p.title(),
                        saveName);
    }

    private static String reactSaveRoute(RouteFrontendParams p) {
        String saveName = p.pascal() + "Save";
        String handleName = saveName + "Handle";
        return """
                import { forwardRef, useImperativeHandle, useState } from 'react'
                import { Form, Input, InputNumber, Radio, message } from 'antd'
                import XnModal from '@/components/XnModal'
                import { create, get, update } from '@/api/%s'
                import { saveDialogTitle, type SaveMode } from '@/types/save'

                export interface %s {
                  open: (mode: SaveMode, id?: number) => Promise<void>
                }

                const %s = forwardRef<%s, { onSuccess?: () => void }>(function %s(
                  { onSuccess },
                  ref,
                ) {
                  const [visible, setVisible] = useState(false)
                  const [mode, setMode] = useState<SaveMode>('add')
                  const [editingId, setEditingId] = useState<number | null>(null)
                  const [submitting, setSubmitting] = useState(false)
                  const [form] = Form.useForm()

                  useImperativeHandle(ref, () => ({
                    async open(openMode, id) {
                      setMode(openMode)
                      setEditingId(id ?? null)
                      form.resetFields()
                      form.setFieldsValue({ name: '', code: '', sort: 0, status: 1, remark: '' })
                      setVisible(true)
                      if (openMode !== 'add' && id) {
                        const res = await get(id)
                        form.setFieldsValue({
                          name: res.data.name,
                          code: res.data.code,
                          sort: res.data.sort,
                          status: res.data.status,
                          remark: res.data.remark || '',
                        })
                      }
                    },
                  }))

                  async function handleSubmit() {
                    const values = await form.validateFields()
                    setSubmitting(true)
                    try {
                      if (mode === 'edit' && editingId) {
                        await update(editingId, values)
                        message.success('更新成功')
                      } else {
                        await create(values)
                        message.success('创建成功')
                      }
                      setVisible(false)
                      onSuccess?.()
                    } finally {
                      setSubmitting(false)
                    }
                  }

                  return (
                    <XnModal
                      title={saveDialogTitle(mode, '%s')}
                      open={visible}
                      onCancel={() => setVisible(false)}
                      destroyOnHidden
                      width={520}
                      okText="保存"
                      cancelText={mode === 'view' ? '关闭' : '取消'}
                      okButtonProps={{ style: mode === 'view' ? { display: 'none' } : undefined }}
                      confirmLoading={submitting}
                      onOk={() => void handleSubmit()}
                    >
                      <Form form={form} labelCol={{ span: 5 }} disabled={mode === 'view'}>
                        <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
                          <Input maxLength={50} />
                        </Form.Item>
                        <Form.Item
                          name="code"
                          label="编码"
                          rules={[
                            { required: true, message: '请输入编码' },
                            { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '字母开头，仅字母数字下划线' },
                          ]}
                        >
                          <Input maxLength={50} />
                        </Form.Item>
                        <Form.Item name="sort" label="排序">
                          <InputNumber min={0} max={9999} style={{ width: '100%%' }} />
                        </Form.Item>
                        <Form.Item name="status" label="状态">
                          <Radio.Group
                            options={[
                              { label: '启用', value: 1 },
                              { label: '停用', value: 0 },
                            ]}
                          />
                        </Form.Item>
                        <Form.Item name="remark" label="备注">
                          <Input.TextArea rows={3} maxLength={200} />
                        </Form.Item>
                      </Form>
                    </XnModal>
                  )
                })

                export default %s
                """
                .formatted(
                        p.prefix(),
                        handleName,
                        saveName,
                        handleName,
                        saveName,
                        p.title(),
                        saveName);
    }

    private static String reactApi(String apiUrl, boolean withStatus) {
        String listParams =
                withStatus
                        ? "export type ListParams = { page: number; size: number; keyword?: string; status?: number }"
                        : "export type ListParams = { page: number; size: number; keyword?: string }";
        return """
                import request from '@/utils/request'
                import type { ApiResponse, PageResult } from '@/types'

                %s

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
                .formatted(listParams, apiUrl, apiUrl, apiUrl, apiUrl, apiUrl, apiUrl);
    }

    // ===================== shared helpers =====================

    private static String formItemVue(ColSpec c) {
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

    private static String formItemReact(ColSpec c) {
        String label = escapeJs(c.label());
        String prop = c.javaField();
        String requiredRule =
                c.required() && "String".equals(c.javaType())
                        ? " rules={[{ required: true, message: '请输入" + label + "' }]}"
                        : "";
        return switch (c.formType()) {
            case "number" ->
                    """
                              <Form.Item name="%s" label="%s"%s>
                                <InputNumber style={{ width: '100%%' }} />
                              </Form.Item>
                            """
                            .formatted(prop, label, requiredRule);
            case "textarea" ->
                    """
                              <Form.Item name="%s" label="%s"%s>
                                <Input.TextArea rows={3} />
                              </Form.Item>
                            """
                            .formatted(prop, label, requiredRule);
            case "select" -> {
                if ("status".equalsIgnoreCase(c.columnName()) || "Integer".equals(c.javaType())) {
                    yield """
                              <Form.Item name="%s" label="%s">
                                <Radio.Group
                                  options={[
                                    { label: '启用', value: 1 },
                                    { label: '停用', value: 0 },
                                  ]}
                                />
                              </Form.Item>
                            """
                            .formatted(prop, label);
                }
                if ("Boolean".equals(c.javaType())) {
                    yield """
                              <Form.Item name="%s" label="%s" valuePropName="checked">
                                <Switch />
                              </Form.Item>
                            """
                            .formatted(prop, label);
                }
                yield """
                          <Form.Item name="%s" label="%s"%s>
                            <Input />
                          </Form.Item>
                        """
                        .formatted(prop, label, requiredRule);
            }
            default ->
                    """
                          <Form.Item name="%s" label="%s"%s>
                            <Input />
                          </Form.Item>
                        """
                            .formatted(prop, label, requiredRule);
        };
    }

    private static String defaultJsValue(ColSpec c) {
        return switch (c.javaType()) {
            case "Integer", "Long", "BigDecimal" -> "0";
            case "Boolean" -> "false";
            default -> "''";
        };
    }

    private static String escapeJs(String value) {
        return value == null ? "" : value.replace("'", "\\'");
    }
}
