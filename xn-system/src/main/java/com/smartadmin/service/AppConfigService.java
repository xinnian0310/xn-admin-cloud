package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.AppConfigVO;
import com.smartadmin.entity.SysAppConfig;
import com.smartadmin.repository.SysAppConfigRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AppConfigService {

    private static final long SINGLETON_ID = 1L;
    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of(
                    "image/png",
                    "image/jpeg",
                    "image/jpg",
                    "image/webp",
                    "image/svg+xml",
                    "image/x-icon",
                    "image/vnd.microsoft.icon");

    private final SysAppConfigRepository repository;
    private final RbacService rbacService;
    private final ObjectMapper objectMapper;
    private final AppCacheService appCacheService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /** 公开读取（登录页品牌等），无需鉴权；无 client 时返回共享兜底 name/intro */
    public AppConfigVO getPublic() {
        return getPublic(null);
    }

    /** 公开读取；指定 clientId 时用 {@code app.clients[clientId]} 覆盖 name / intro。 */
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

    @Transactional
    public AppConfigVO update(AppConfigVO request) {
        rbacService.checkPermission("system-config:update");
        if (request == null) {
            throw new BusinessException("配置不能为空");
        }
        validate(request);
        normalize(request);
        SysAppConfig entity =
                repository
                        .findById(SINGLETON_ID)
                        .orElseGet(
                                () -> {
                                    SysAppConfig created = new SysAppConfig();
                                    created.setId(SINGLETON_ID);
                                    return created;
                                });
        try {
            entity.setConfigJson(objectMapper.writeValueAsString(request));
        } catch (Exception e) {
            throw new BusinessException("配置序列化失败");
        }
        repository.save(entity);
        appCacheService.evictAppConfig();
        return request;
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
        AppConfigVO vo =
                repository.findById(SINGLETON_ID).map(this::parse).orElseGet(AppConfigVO::new);
        boolean clientsChanged = prepareAppClients(vo);
        normalize(vo);
        if (clientsChanged) {
            persistInternal(vo);
        }
        return vo;
    }

    private AppConfigVO parse(SysAppConfig entity) {
        if (!StringUtils.hasText(entity.getConfigJson())) {
            return new AppConfigVO();
        }
        try {
            AppConfigVO vo = objectMapper.readValue(entity.getConfigJson(), AppConfigVO.class);
            return vo != null ? vo : new AppConfigVO();
        } catch (Exception e) {
            return new AppConfigVO();
        }
    }

    /** 确保 app.clients 可写，并补齐 / 迁移各前端 name·intro；返回是否有变更需落库。 */
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
        if (vo.getLogRetention() == null) {
            vo.setLogRetention(new AppConfigVO.LogRetentionConfig());
        }
        vo.setSensitiveData(normalizeSensitiveData(vo.getSensitiveData()));
        return vo;
    }

    /**
     * 补齐 / 修复各前端工程 name·intro，并迁移旧 clientId。
     *
     * <p>若库里仍是旧的「共享介绍」被写进多个 client，会按技术栈默认值拆开回填。
     *
     * @return 是否有变更（需落库）
     */
    private boolean syncClientProfiles(AppConfigVO.AppInfo app) {
        boolean changed = false;
        Map<String, AppConfigVO.ClientAppProfile> clients = app.getClients();

        // 旧 id：xn-admin-vue3-options-js → xn-admin-vue2-js
        if (clients.containsKey("xn-admin-vue3-options-js")) {
            AppConfigVO.ClientAppProfile legacy = clients.remove("xn-admin-vue3-options-js");
            clients.putIfAbsent(
                    "xn-admin-vue2-js",
                    legacy != null
                            ? legacy
                            : AppConfigVO.AppInfo.defaultClientProfiles().get("xn-admin-vue2-js"));
            changed = true;
        }

        // 根级旧共享介绍清空（介绍只认 clients）
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
            // 空介绍、旧文案、或误用了其他工程的默认介绍 → 换成该技术栈当前默认
            if (shouldReplaceClientIntro(existing.getIntro(), clientId, def.getIntro())) {
                existing.setIntro(def.getIntro());
                changed = true;
            }
        }
        return changed;
    }

    /**
     * 是否用默认介绍覆盖现有值。
     *
     * @param intro 当前介绍
     * @param clientId 当前工程；根级传 null
     * @param desiredIntro 该工程目标默认介绍
     */
    private static boolean shouldReplaceClientIntro(
            String intro, String clientId, String desiredIntro) {
        if (!StringUtils.hasText(intro)) {
            // 仅当有目标文案时才用默认填空；根级 desired 为空则不动
            return StringUtils.hasText(desiredIntro);
        }
        String t = intro.trim();
        if (StringUtils.hasText(desiredIntro) && t.equals(desiredIntro.trim())) {
            return false;
        }
        if (LEGACY_INTROS.contains(t)) {
            return true;
        }
        // 误用了其他工程的默认介绍
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
                    // 旧共享根级（未写 JavaScript）
                    "面向中后台的 Vue3 + 微服务管理脚手架：JWT 登录、RBAC 动态路由、page-ui 驱动 CRUD、多布局与主题、通知推送与系统监控一站集成，对接 xn-admin-cloud 网关即可开箱使用。",
                    // 上一版 vue3-js / vue2-js 默认
                    "与 xn-admin-vue3-ts 功能、界面完全对齐的 Vue3 + JavaScript 版本，采用 Composition API / <script setup>。适合熟悉组合式 API、希望少写类型注解的团队，同样对接 xn-admin-cloud。",
                    "与 xn-admin-vue3-ts 功能、界面完全对齐的 JavaScript 版本，采用 Composition API / <script setup>。适合熟悉组合式 API、希望少写类型注解的团队，同样对接 xn-admin-cloud。",
                    "面向中后台的 Vue2 + JavaScript 管理脚手架：对接同一套 xn-admin-cloud 微服务后端，适合 Vue2 技术栈团队或渐进迁移场景。",
                    "与 xn-admin-vue3-ts 功能、界面完全对齐的 JavaScript 版本，采用经典 Options API（data / methods / computed / watch）。适合从 Vue 2 迁移或更习惯选项式写法的团队，同样对接 xn-admin-cloud。");

    /** 无权限校验的内部落库（用于补齐默认 clients） */
    @Transactional
    protected void persistInternal(AppConfigVO vo) {
        try {
            SysAppConfig entity =
                    repository
                            .findById(SINGLETON_ID)
                            .orElseGet(
                                    () -> {
                                        SysAppConfig created = new SysAppConfig();
                                        created.setId(SINGLETON_ID);
                                        return created;
                                    });
            entity.setConfigJson(objectMapper.writeValueAsString(vo));
            repository.save(entity);
            appCacheService.evictAppConfig();
        } catch (Exception e) {
            throw new BusinessException("写入默认前端应用信息失败");
        }
    }

    /** 按前端 clientId 投影品牌文案；不修改缓存中的原始配置。 */
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

    /** 供业务读取脱敏策略（走缓存公开配置） */
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
            cfg.setFields(new java.util.ArrayList<>(java.util.List.of("phone", "email")));
        } else {
            java.util.List<String> cleaned =
                    cfg.getFields().stream()
                            .filter(f -> f != null && !f.isBlank())
                            .map(f -> f.trim().toLowerCase())
                            .filter(ALLOWED_SENSITIVE_FIELDS::contains)
                            .distinct()
                            .toList();
            cfg.setFields(
                    new java.util.ArrayList<>(
                            cleaned.isEmpty() ? java.util.List.of("phone", "email") : cleaned));
        }
        return cfg;
    }

    private static final java.util.Set<String> ALLOWED_SENSITIVE_FIELDS =
            java.util.Set.of("phone", "email");

    private void validate(AppConfigVO vo) {
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
                throw new BusinessException("语言包无效");
            }
            String size = vo.getUi().getElementPlus().getSize();
            if (StringUtils.hasText(size) && !Set.of("large", "default", "small").contains(size)) {
                throw new BusinessException("组件尺寸无效");
            }
        }
        if (vo.getLogRetention() != null) {
            validateRetentionDays(vo.getLogRetention().getLoginDays(), "登录日志保留天数");
            validateRetentionDays(vo.getLogRetention().getOperDays(), "操作日志保留天数");
            validateRetentionDays(vo.getLogRetention().getExceptionDays(), "异常日志保留天数");
            validateRetentionDays(vo.getLogRetention().getJobDays(), "任务日志保留天数");
        }
        if (vo.getSensitiveData() != null && vo.getSensitiveData().getFields() != null) {
            for (String field : vo.getSensitiveData().getFields()) {
                if (field == null || field.isBlank()) {
                    continue;
                }
                if (!ALLOWED_SENSITIVE_FIELDS.contains(field.trim().toLowerCase())) {
                    throw new BusinessException("不支持的敏感字段: " + field + "（仅支持 phone、email）");
                }
            }
        }
        // 写入前再规范化
        vo.setSensitiveData(normalizeSensitiveData(vo.getSensitiveData()));
    }

    private void validateRetentionDays(Integer days, String label) {
        if (days == null) {
            return;
        }
        if (days < 0 || days > 3650) {
            throw new BusinessException(label + "须在 0~3650 之间");
        }
    }

    /** 系统配置字号/高度：必填正整数，统一为 Npx */
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
