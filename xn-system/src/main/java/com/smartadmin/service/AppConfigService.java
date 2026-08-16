package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.AppConfigVO;
import com.smartadmin.dto.StorageSectionVO;
import com.smartadmin.entity.SysAppConfig;
import com.smartadmin.entity.SysCfgApp;
import com.smartadmin.entity.SysCfgLogRetention;
import com.smartadmin.entity.SysCfgSensitiveData;
import com.smartadmin.entity.SysCfgSession;
import com.smartadmin.entity.SysCfgStorage;
import com.smartadmin.entity.SysCfgUi;
import com.smartadmin.repository.SysAppConfigRepository;
import com.smartadmin.repository.SysCfgAppRepository;
import com.smartadmin.repository.SysCfgLogRetentionRepository;
import com.smartadmin.repository.SysCfgSensitiveDataRepository;
import com.smartadmin.repository.SysCfgSessionRepository;
import com.smartadmin.repository.SysCfgStorageRepository;
import com.smartadmin.repository.SysCfgUiRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AppConfigService {

    public static final String SECTION_APP = "app";
    public static final String SECTION_SESSION = "session";
    public static final String SECTION_UI = "ui";
    public static final String SECTION_STORAGE = "storage";
    public static final String SECTION_LOG_RETENTION = "logRetention";
    public static final String SECTION_SENSITIVE_DATA = "sensitiveData";

    private static final long SINGLETON_ID = 1L;
    private static final Set<String> SECTIONS =
            Set.of(
                    SECTION_APP,
                    SECTION_SESSION,
                    SECTION_UI,
                    SECTION_STORAGE,
                    SECTION_LOG_RETENTION,
                    SECTION_SENSITIVE_DATA);
    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of(
                    "image/png",
                    "image/jpeg",
                    "image/jpg",
                    "image/webp",
                    "image/svg+xml",
                    "image/x-icon",
                    "image/vnd.microsoft.icon");
    private static final Set<String> ALLOWED_SENSITIVE_FIELDS = Set.of("phone", "email");

    private final SysAppConfigRepository legacyRepository;
    private final SysCfgAppRepository appRepository;
    private final SysCfgSessionRepository sessionRepository;
    private final SysCfgUiRepository uiRepository;
    private final SysCfgLogRetentionRepository logRetentionRepository;
    private final SysCfgSensitiveDataRepository sensitiveDataRepository;
    private final SysCfgStorageRepository storageRepository;
    private final RbacService rbacService;
    private final ObjectMapper objectMapper;
    private final AppCacheService appCacheService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public AppConfigVO getPublic() {
        return getPublic(null);
    }

    public AppConfigVO getPublic(String clientId) {
        AppConfigVO raw =
                appCacheService.getAppConfig(
                        new tools.jackson.core.type.TypeReference<>() {}, this::loadOrDefault);
        return projectForClient(raw, clientId);
    }

    public AppConfigVO getForAdmin() {
        rbacService.checkPermission("system-config:view");
        return loadOrDefault();
    }

    /** 读取单个分区：storage 返回 {@link StorageSectionVO}，其余返回对应分区对象。 */
    public Object getSection(String section) {
        String key = normalizeSection(section);
        if (SECTION_STORAGE.equals(key)) {
            rbacService.checkPermission("remote-storage:view");
        } else {
            rbacService.checkPermission("system-config:view");
        }
        AppConfigVO vo = loadOrDefault();
        return switch (key) {
            case SECTION_APP -> vo.getApp();
            case SECTION_SESSION -> vo.getSession();
            case SECTION_UI -> vo.getUi();
            case SECTION_STORAGE -> storageToSection(vo.getStorage());
            case SECTION_LOG_RETENTION -> vo.getLogRetention();
            case SECTION_SENSITIVE_DATA -> vo.getSensitiveData();
            default -> throw new BusinessException("未知配置分区: " + section);
        };
    }

    /** 只保存一个分区。storage 请求体为 {@link StorageSectionVO}（items）；其余为分区对象本身。 返回聚合后的完整配置。 */
    @Transactional
    public AppConfigVO updateSection(String section, JsonNode body) {
        String key = normalizeSection(section);
        if (SECTION_STORAGE.equals(key)) {
            rbacService.checkPermission("remote-storage:update");
        } else {
            rbacService.checkPermission("system-config:update");
        }
        if (body == null || body.isNull()) {
            throw new BusinessException("配置不能为空");
        }
        // 确保已从旧表迁移
        loadOrDefault();
        try {
            switch (key) {
                case SECTION_APP -> {
                    AppConfigVO.AppInfo app =
                            objectMapper.treeToValue(body, AppConfigVO.AppInfo.class);
                    if (app == null) {
                        throw new BusinessException("应用信息不能为空");
                    }
                    AppConfigVO tmp = new AppConfigVO();
                    tmp.setApp(app);
                    validateApp(tmp);
                    prepareAppClients(tmp);
                    persistApp(tmp.getApp());
                }
                case SECTION_SESSION -> {
                    AppConfigVO.SessionConfig session =
                            objectMapper.treeToValue(body, AppConfigVO.SessionConfig.class);
                    if (session == null) {
                        session = new AppConfigVO.SessionConfig();
                    }
                    persistSession(session);
                }
                case SECTION_UI -> {
                    AppConfigVO.UiConfig ui =
                            objectMapper.treeToValue(body, AppConfigVO.UiConfig.class);
                    if (ui == null) {
                        ui = new AppConfigVO.UiConfig();
                    }
                    AppConfigVO tmp = new AppConfigVO();
                    tmp.setUi(ui);
                    validateUi(tmp);
                    persistUi(ui);
                }
                case SECTION_STORAGE -> {
                    StorageSectionVO sectionVo =
                            objectMapper.treeToValue(body, StorageSectionVO.class);
                    if (sectionVo == null) {
                        sectionVo = new StorageSectionVO();
                    }
                    replaceStorage(sectionVo);
                }
                case SECTION_LOG_RETENTION -> {
                    AppConfigVO.LogRetentionConfig lr =
                            objectMapper.treeToValue(body, AppConfigVO.LogRetentionConfig.class);
                    if (lr == null) {
                        lr = new AppConfigVO.LogRetentionConfig();
                    }
                    validateLogRetention(lr);
                    persistLogRetention(lr);
                }
                case SECTION_SENSITIVE_DATA -> {
                    AppConfigVO.SensitiveDataConfig sd =
                            objectMapper.treeToValue(body, AppConfigVO.SensitiveDataConfig.class);
                    sd = normalizeSensitiveData(sd);
                    validateSensitiveData(sd);
                    persistSensitiveData(sd);
                }
                default -> throw new BusinessException("未知配置分区: " + section);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("配置解析失败：" + e.getMessage());
        }
        appCacheService.evictAppConfig();
        return loadOrDefault();
    }

    @Transactional
    public String uploadAsset(MultipartFile file) {
        rbacService.checkPermission("system-config:update");
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException("仅支持 png / jpg / webp / svg / ico 图片");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (StringUtils.hasText(original) && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase();
        } else if (contentType.contains("svg")) {
            ext = ".svg";
        } else if (contentType.contains("png")) {
            ext = ".png";
        } else if (contentType.contains("webp")) {
            ext = ".webp";
        } else if (contentType.contains("icon")) {
            ext = ".ico";
        } else {
            ext = ".jpg";
        }
        try {
            Path dir = Paths.get(uploadDir, "brand").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String filename = UUID.randomUUID().toString().replace("-", "") + ext;
            Path target = dir.resolve(filename);
            file.transferTo(target);
            return "/uploads/brand/" + filename;
        } catch (IOException e) {
            throw new BusinessException("上传失败");
        }
    }

    private AppConfigVO loadOrDefault() {
        migrateFromLegacyIfNeeded();
        AppConfigVO vo = new AppConfigVO();
        vo.setApp(
                readJson(
                        appRepository.findById(SINGLETON_ID).map(SysCfgApp::getConfigJson),
                        AppConfigVO.AppInfo.class,
                        new AppConfigVO.AppInfo()));
        vo.setSession(
                readJson(
                        sessionRepository.findById(SINGLETON_ID).map(SysCfgSession::getConfigJson),
                        AppConfigVO.SessionConfig.class,
                        new AppConfigVO.SessionConfig()));
        vo.setUi(
                readJson(
                        uiRepository.findById(SINGLETON_ID).map(SysCfgUi::getConfigJson),
                        AppConfigVO.UiConfig.class,
                        new AppConfigVO.UiConfig()));
        vo.setLogRetention(
                readJson(
                        logRetentionRepository
                                .findById(SINGLETON_ID)
                                .map(SysCfgLogRetention::getConfigJson),
                        AppConfigVO.LogRetentionConfig.class,
                        new AppConfigVO.LogRetentionConfig()));
        vo.setSensitiveData(
                readJson(
                        sensitiveDataRepository
                                .findById(SINGLETON_ID)
                                .map(SysCfgSensitiveData::getConfigJson),
                        AppConfigVO.SensitiveDataConfig.class,
                        new AppConfigVO.SensitiveDataConfig()));
        vo.setStorage(loadStorageMap());
        prepareAppClients(vo);
        normalize(vo);
        // 只补空分区行，不把内存默认值回写已有应用信息（名称 / logo / favicon）
        ensureSectionDefaults(vo);
        return vo;
    }

    private void ensureSectionDefaults(AppConfigVO vo) {
        if (!appRepository.existsById(SINGLETON_ID)) {
            persistApp(vo.getApp());
        }
        if (!sessionRepository.existsById(SINGLETON_ID)) {
            persistSession(vo.getSession());
        }
        if (!uiRepository.existsById(SINGLETON_ID)) {
            persistUi(vo.getUi());
        }
        if (!logRetentionRepository.existsById(SINGLETON_ID)) {
            persistLogRetention(vo.getLogRetention());
        }
        if (!sensitiveDataRepository.existsById(SINGLETON_ID)) {
            persistSensitiveData(vo.getSensitiveData());
        }
    }

    /** 旧 sys_app_config 单例 JSON → 分区分表（仅当分区表尚无数据时）。 */
    private void migrateFromLegacyIfNeeded() {
        boolean anySection =
                appRepository.existsById(SINGLETON_ID)
                        || sessionRepository.existsById(SINGLETON_ID)
                        || uiRepository.existsById(SINGLETON_ID)
                        || logRetentionRepository.existsById(SINGLETON_ID)
                        || sensitiveDataRepository.existsById(SINGLETON_ID)
                        || storageRepository.count() > 0;
        if (anySection) {
            return;
        }
        SysAppConfig legacy = legacyRepository.findById(SINGLETON_ID).orElse(null);
        if (legacy == null || !StringUtils.hasText(legacy.getConfigJson())) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(legacy.getConfigJson());
            if (root == null || !root.isObject()) {
                return;
            }
            AppConfigVO defaults = new AppConfigVO();
            if (root.has(SECTION_APP)) {
                persistApp(
                        readNode(
                                root.get(SECTION_APP),
                                AppConfigVO.AppInfo.class,
                                defaults.getApp()));
            }
            if (root.has(SECTION_SESSION)) {
                persistSession(
                        readNode(
                                root.get(SECTION_SESSION),
                                AppConfigVO.SessionConfig.class,
                                defaults.getSession()));
            }
            if (root.has(SECTION_UI)) {
                persistUi(
                        readNode(
                                root.get(SECTION_UI),
                                AppConfigVO.UiConfig.class,
                                defaults.getUi()));
            }
            if (root.has(SECTION_LOG_RETENTION)) {
                persistLogRetention(
                        readNode(
                                root.get(SECTION_LOG_RETENTION),
                                AppConfigVO.LogRetentionConfig.class,
                                defaults.getLogRetention()));
            }
            if (root.has(SECTION_SENSITIVE_DATA)) {
                persistSensitiveData(
                        normalizeSensitiveData(
                                readNode(
                                        root.get(SECTION_SENSITIVE_DATA),
                                        AppConfigVO.SensitiveDataConfig.class,
                                        defaults.getSensitiveData())));
            }
            if (root.has(SECTION_STORAGE)) {
                Map<String, String> map = convertLegacyStorage(root.get(SECTION_STORAGE));
                if (!map.isEmpty()) {
                    StorageSectionVO section = storageToSection(map);
                    replaceStorage(section);
                }
            }
        } catch (Exception ignored) {
            // 迁移失败不影响启动，后续使用默认值
        }
    }

    private Map<String, String> convertLegacyStorage(JsonNode node) {
        Map<String, String> map = new LinkedHashMap<>();
        if (node == null || node.isNull()) {
            return map;
        }
        if (node.isString()) {
            String path = normalizePath(node.asString());
            if (StringUtils.hasText(path)) {
                map.put("minio", path);
            }
            return map;
        }
        if (!node.isObject()) {
            return map;
        }
        // 新格式：{ "minio": "http://..." }
        boolean allText = true;
        for (String key : node.propertyNames()) {
            JsonNode v = node.get(key);
            if (v != null && v.isString()) {
                String path = normalizePath(v.asString());
                if (StringUtils.hasText(key) && StringUtils.hasText(path)) {
                    map.put(key.trim(), path);
                }
            } else if (v != null && !v.isNull()) {
                allText = false;
            }
        }
        if (!map.isEmpty() && allText) {
            return map;
        }
        // 旧格式：{ "minio": { endpoint, bucket, region } }
        JsonNode minio = node.get("minio");
        if (minio != null && minio.isObject()) {
            String endpoint =
                    minio.has("endpoint") && minio.get("endpoint").isString()
                            ? minio.get("endpoint").asString().trim()
                            : "";
            String bucket =
                    minio.has("bucket") && minio.get("bucket").isString()
                            ? minio.get("bucket").asString().trim()
                            : "";
            if (StringUtils.hasText(endpoint)) {
                String base = endpoint.replaceAll("/+$", "");
                String path =
                        StringUtils.hasText(bucket)
                                ? base + "/" + bucket.replaceAll("^/+|/+$", "") + "/"
                                : base + "/";
                map.put("minio", path);
            }
        }
        return map;
    }

    private Map<String, String> loadStorageMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (SysCfgStorage row : storageRepository.findAll()) {
            if (!StringUtils.hasText(row.getName()) || !StringUtils.hasText(row.getPath())) {
                continue;
            }
            map.put(row.getName().trim(), normalizePath(row.getPath()));
        }
        return map;
    }

    private void replaceStorage(StorageSectionVO section) {
        List<StorageSectionVO.Item> items =
                section.getItems() == null ? List.of() : section.getItems();
        Map<String, String> seen = new LinkedHashMap<>();
        for (StorageSectionVO.Item item : items) {
            if (item == null) {
                continue;
            }
            String name = item.getName() == null ? "" : item.getName().trim();
            String path = normalizePath(item.getPath());
            if (!StringUtils.hasText(name)) {
                throw new BusinessException("对象存储「名字」不能为空");
            }
            if (!name.matches("^[a-zA-Z][a-zA-Z0-9_-]{0,63}$")) {
                throw new BusinessException("对象存储名字无效: " + name + "（字母开头，仅字母数字_ -）");
            }
            if (!StringUtils.hasText(path)) {
                throw new BusinessException("对象存储「路径」不能为空");
            }
            if (seen.containsKey(name)) {
                throw new BusinessException("对象存储名字重复: " + name);
            }
            seen.put(name, path);
        }
        storageRepository.deleteAllInBatch();
        storageRepository.flush();
        for (Map.Entry<String, String> e : seen.entrySet()) {
            SysCfgStorage row = new SysCfgStorage();
            row.setName(e.getKey());
            row.setPath(e.getValue());
            storageRepository.save(row);
        }
    }

    private static StorageSectionVO storageToSection(Map<String, String> map) {
        StorageSectionVO vo = new StorageSectionVO();
        if (map == null) {
            return vo;
        }
        for (Map.Entry<String, String> e : map.entrySet()) {
            vo.getItems().add(new StorageSectionVO.Item(e.getKey(), e.getValue()));
        }
        return vo;
    }

    private static String normalizePath(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String path = raw.trim();
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        return path;
    }

    private String normalizeSection(String section) {
        if (!StringUtils.hasText(section)) {
            throw new BusinessException("分区不能为空");
        }
        String key = section.trim();
        if (!SECTIONS.contains(key)) {
            throw new BusinessException("未知配置分区: " + section);
        }
        return key;
    }

    private void persistApp(AppConfigVO.AppInfo app) {
        writeJson(
                appRepository
                        .findById(SINGLETON_ID)
                        .orElseGet(
                                () -> {
                                    SysCfgApp e = new SysCfgApp();
                                    e.setId(SINGLETON_ID);
                                    return e;
                                }),
                app,
                (entity, json) -> {
                    entity.setConfigJson(json);
                    appRepository.save(entity);
                });
    }

    private void persistSession(AppConfigVO.SessionConfig session) {
        writeJson(
                sessionRepository
                        .findById(SINGLETON_ID)
                        .orElseGet(
                                () -> {
                                    SysCfgSession e = new SysCfgSession();
                                    e.setId(SINGLETON_ID);
                                    return e;
                                }),
                session,
                (entity, json) -> {
                    entity.setConfigJson(json);
                    sessionRepository.save(entity);
                });
    }

    private void persistUi(AppConfigVO.UiConfig ui) {
        writeJson(
                uiRepository
                        .findById(SINGLETON_ID)
                        .orElseGet(
                                () -> {
                                    SysCfgUi e = new SysCfgUi();
                                    e.setId(SINGLETON_ID);
                                    return e;
                                }),
                ui,
                (entity, json) -> {
                    entity.setConfigJson(json);
                    uiRepository.save(entity);
                });
    }

    private void persistLogRetention(AppConfigVO.LogRetentionConfig lr) {
        writeJson(
                logRetentionRepository
                        .findById(SINGLETON_ID)
                        .orElseGet(
                                () -> {
                                    SysCfgLogRetention e = new SysCfgLogRetention();
                                    e.setId(SINGLETON_ID);
                                    return e;
                                }),
                lr,
                (entity, json) -> {
                    entity.setConfigJson(json);
                    logRetentionRepository.save(entity);
                });
    }

    private void persistSensitiveData(AppConfigVO.SensitiveDataConfig sd) {
        writeJson(
                sensitiveDataRepository
                        .findById(SINGLETON_ID)
                        .orElseGet(
                                () -> {
                                    SysCfgSensitiveData e = new SysCfgSensitiveData();
                                    e.setId(SINGLETON_ID);
                                    return e;
                                }),
                sd,
                (entity, json) -> {
                    entity.setConfigJson(json);
                    sensitiveDataRepository.save(entity);
                });
    }

    @FunctionalInterface
    private interface JsonSaver<T> {
        void save(T entity, String json);
    }

    private <T, E> void writeJson(E entity, T value, JsonSaver<E> saver) {
        try {
            saver.save(entity, objectMapper.writeValueAsString(value));
        } catch (Exception e) {
            throw new BusinessException("配置序列化失败");
        }
    }

    private <T> T readJson(java.util.Optional<String> jsonOpt, Class<T> type, T defaultValue) {
        if (jsonOpt.isEmpty() || !StringUtils.hasText(jsonOpt.get())) {
            return defaultValue;
        }
        try {
            T value = objectMapper.readValue(jsonOpt.get(), type);
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private <T> T readNode(JsonNode node, Class<T> type, T defaultValue) {
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        try {
            T value = objectMapper.treeToValue(node, type);
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private boolean prepareAppClients(AppConfigVO vo) {
        if (vo.getApp() == null) {
            vo.setApp(new AppConfigVO.AppInfo());
        }
        if (vo.getApp().getClients() == null) {
            vo.getApp().setClients(new LinkedHashMap<>());
        }
        return syncClientProfiles(vo.getApp());
    }

    private AppConfigVO normalize(AppConfigVO vo) {
        if (vo.getApp() == null) {
            vo.setApp(new AppConfigVO.AppInfo());
        }
        if (vo.getApp().getClients() == null) {
            vo.getApp().setClients(new LinkedHashMap<>());
        }
        syncClientProfiles(vo.getApp());
        if (vo.getSession() == null) {
            vo.setSession(new AppConfigVO.SessionConfig());
        }
        if (vo.getUi() == null) {
            vo.setUi(new AppConfigVO.UiConfig());
        }
        if (vo.getLogRetention() == null) {
            vo.setLogRetention(new AppConfigVO.LogRetentionConfig());
        }
        if (vo.getStorage() == null) {
            vo.setStorage(new LinkedHashMap<>());
        }
        vo.setSensitiveData(normalizeSensitiveData(vo.getSensitiveData()));
        return vo;
    }

    private boolean syncClientProfiles(AppConfigVO.AppInfo app) {
        boolean changed = false;
        Map<String, AppConfigVO.ClientAppProfile> clients = app.getClients();

        if (clients.containsKey("xn-admin-vue3-options-js")) {
            AppConfigVO.ClientAppProfile legacy = clients.remove("xn-admin-vue3-options-js");
            clients.putIfAbsent(
                    "xn-admin-vue2-js",
                    legacy != null
                            ? legacy
                            : AppConfigVO.AppInfo.defaultClientProfiles().get("xn-admin-vue2-js"));
            changed = true;
        }

        if (shouldReplaceClientIntro(app.getIntro(), null, "")) {
            app.setIntro("");
            changed = true;
        }

        for (Map.Entry<String, AppConfigVO.ClientAppProfile> entry :
                AppConfigVO.AppInfo.defaultClientProfiles().entrySet()) {
            String clientId = entry.getKey();
            AppConfigVO.ClientAppProfile def = entry.getValue();
            AppConfigVO.ClientAppProfile existing = clients.get(clientId);
            if (existing == null) {
                clients.put(clientId, def);
                changed = true;
                continue;
            }
            if (!StringUtils.hasText(existing.getName())) {
                existing.setName(def.getName());
                changed = true;
            }
            if (shouldReplaceClientIntro(existing.getIntro(), clientId, def.getIntro())) {
                existing.setIntro(def.getIntro());
                changed = true;
            }
        }
        return changed;
    }

    private static boolean shouldReplaceClientIntro(
            String intro, String clientId, String desiredIntro) {
        if (!StringUtils.hasText(intro)) {
            return StringUtils.hasText(desiredIntro);
        }
        String t = intro.trim();
        if (StringUtils.hasText(desiredIntro) && t.equals(desiredIntro.trim())) {
            return false;
        }
        if (LEGACY_INTROS.contains(t)) {
            return true;
        }
        if (StringUtils.hasText(clientId)) {
            for (Map.Entry<String, AppConfigVO.ClientAppProfile> entry :
                    AppConfigVO.AppInfo.defaultClientProfiles().entrySet()) {
                if (entry.getKey().equals(clientId)) {
                    continue;
                }
                String other = entry.getValue() != null ? entry.getValue().getIntro() : null;
                if (StringUtils.hasText(other) && t.equals(other.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final Set<String> LEGACY_INTROS =
            Set.of(
                    "面向中后台的 Vue3 + 微服务管理脚手架：JWT 登录、RBAC 动态路由、page-ui 驱动 CRUD、多布局与主题、通知推送与系统监控一站集成，对接 xn-admin-cloud 网关即可开箱使用。",
                    "与 xn-admin-vue3-ts 功能、界面完全对齐的 Vue3 + JavaScript 版本，采用 Composition API / <script setup>。适合熟悉组合式 API、希望少写类型注解的团队，同样对接 xn-admin-cloud。",
                    "与 xn-admin-vue3-ts 功能、界面完全对齐的 JavaScript 版本，采用 Composition API / <script setup>。适合熟悉组合式 API、希望少写类型注解的团队，同样对接 xn-admin-cloud。",
                    "面向中后台的 Vue2 + JavaScript 管理脚手架：对接同一套 xn-admin-cloud 微服务后端，适合 Vue2 技术栈团队或渐进迁移场景。",
                    "与 xn-admin-vue3-ts 功能、界面完全对齐的 JavaScript 版本，采用经典 Options API（data / methods / computed / watch）。适合从 Vue 2 迁移或更习惯选项式写法的团队，同样对接 xn-admin-cloud。");

    private AppConfigVO projectForClient(AppConfigVO raw, String clientId) {
        if (raw == null) {
            return new AppConfigVO();
        }
        AppConfigVO copy;
        try {
            copy = objectMapper.readValue(objectMapper.writeValueAsString(raw), AppConfigVO.class);
        } catch (Exception e) {
            return normalize(raw);
        }
        normalize(copy);
        if (!StringUtils.hasText(clientId) || copy.getApp().getClients().isEmpty()) {
            return copy;
        }
        AppConfigVO.ClientAppProfile profile = copy.getApp().getClients().get(clientId.trim());
        if (profile != null) {
            if (StringUtils.hasText(profile.getName())) {
                copy.getApp().setName(profile.getName());
            }
            if (profile.getIntro() != null) {
                copy.getApp().setIntro(profile.getIntro());
            }
        }
        return copy;
    }

    public AppConfigVO.SensitiveDataConfig resolveSensitiveData() {
        return normalize(getPublic()).getSensitiveData();
    }

    private AppConfigVO.SensitiveDataConfig normalizeSensitiveData(
            AppConfigVO.SensitiveDataConfig cfg) {
        if (cfg == null) {
            cfg = new AppConfigVO.SensitiveDataConfig();
        }
        if (cfg.getEnabled() == null) {
            cfg.setEnabled(true);
        }
        if (cfg.getFields() == null || cfg.getFields().isEmpty()) {
            cfg.setFields(new ArrayList<>(List.of("phone", "email")));
        } else {
            List<String> cleaned =
                    cfg.getFields().stream()
                            .filter(f -> f != null && !f.isBlank())
                            .map(f -> f.trim().toLowerCase())
                            .filter(ALLOWED_SENSITIVE_FIELDS::contains)
                            .distinct()
                            .toList();
            cfg.setFields(new ArrayList<>(cleaned.isEmpty() ? List.of("phone", "email") : cleaned));
        }
        return cfg;
    }

    private void validateApp(AppConfigVO vo) {
        if (vo.getApp() == null || !StringUtils.hasText(vo.getApp().getName())) {
            throw new BusinessException("项目名称不能为空");
        }
        if (vo.getApp().getClients() != null) {
            for (Map.Entry<String, AppConfigVO.ClientAppProfile> entry :
                    vo.getApp().getClients().entrySet()) {
                String clientId = entry.getKey();
                if (!StringUtils.hasText(clientId)
                        || !clientId.trim().matches("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$")) {
                    throw new BusinessException("无效的前端 clientId: " + clientId);
                }
                AppConfigVO.ClientAppProfile profile = entry.getValue();
                if (profile != null && !StringUtils.hasText(profile.getName())) {
                    throw new BusinessException("前端「" + clientId.trim() + "」的项目名称不能为空");
                }
            }
        }
    }

    private void validateUi(AppConfigVO vo) {
        if (vo.getUi() != null && vo.getUi().getLayout() != null) {
            String mode = vo.getUi().getLayout().getMode();
            if (StringUtils.hasText(mode)
                    && !Set.of("side", "top", "mix", "columns").contains(mode)) {
                throw new BusinessException("布局模式无效");
            }
        }
        if (vo.getUi() != null) {
            if (vo.getUi().getFontSize() != null) {
                vo.getUi()
                        .getFontSize()
                        .setSidebar(
                                normalizeRequiredPx(vo.getUi().getFontSize().getSidebar(), "侧栏字号"));
                vo.getUi()
                        .getFontSize()
                        .setHeader(
                                normalizeRequiredPx(vo.getUi().getFontSize().getHeader(), "顶栏字号"));
                vo.getUi()
                        .getFontSize()
                        .setTagsView(
                                normalizeRequiredPx(
                                        vo.getUi().getFontSize().getTagsView(), "标签栏字号"));
                vo.getUi()
                        .getFontSize()
                        .setMain(normalizeRequiredPx(vo.getUi().getFontSize().getMain(), "正文字号"));
            }
            if (vo.getUi().getTagsView() != null) {
                vo.getUi()
                        .getTagsView()
                        .setHeight(
                                normalizeRequiredPx(vo.getUi().getTagsView().getHeight(), "标签栏高度"));
            }
        }
        if (vo.getUi() != null && vo.getUi().getElementPlus() != null) {
            String locale = vo.getUi().getElementPlus().getLocale();
            if (StringUtils.hasText(locale) && !Set.of("zh-cn", "en").contains(locale)) {
                throw new BusinessException("Element Plus 语言包无效");
            }
            String size = vo.getUi().getElementPlus().getSize();
            if (StringUtils.hasText(size) && !Set.of("large", "default", "small").contains(size)) {
                throw new BusinessException("Element Plus 组件尺寸无效");
            }
        }
        if (vo.getUi() != null && vo.getUi().getAntd() != null) {
            String locale = vo.getUi().getAntd().getLocale();
            if (StringUtils.hasText(locale) && !Set.of("zh-cn", "en").contains(locale)) {
                throw new BusinessException("Ant Design 语言包无效");
            }
            String size = vo.getUi().getAntd().getComponentSize();
            if (StringUtils.hasText(size) && !Set.of("large", "middle", "small").contains(size)) {
                throw new BusinessException("Ant Design 组件尺寸无效");
            }
        }
    }

    private void validateLogRetention(AppConfigVO.LogRetentionConfig lr) {
        validateRetentionDays(lr.getLoginDays(), "登录日志保留天数");
        validateRetentionDays(lr.getOperDays(), "操作日志保留天数");
        validateRetentionDays(lr.getExceptionDays(), "异常日志保留天数");
        validateRetentionDays(lr.getJobDays(), "任务日志保留天数");
    }

    private void validateSensitiveData(AppConfigVO.SensitiveDataConfig sd) {
        if (sd.getFields() == null) {
            return;
        }
        for (String field : sd.getFields()) {
            if (field == null || field.isBlank()) {
                continue;
            }
            if (!ALLOWED_SENSITIVE_FIELDS.contains(field.trim().toLowerCase())) {
                throw new BusinessException("不支持的敏感字段: " + field + "（仅支持 phone、email）");
            }
        }
    }

    private void validateRetentionDays(Integer days, String label) {
        if (days == null) {
            return;
        }
        if (days < 0 || days > 3650) {
            throw new BusinessException(label + "须在 0~3650 之间");
        }
    }

    private String normalizeRequiredPx(String raw, String label) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(label + "不能为空");
        }
        String text = raw.trim().toLowerCase().replace("px", "").trim();
        if (!text.matches("^[1-9]\\d*$")) {
            throw new BusinessException(label + "须为正整数（单位 px）");
        }
        return text + "px";
    }
}
