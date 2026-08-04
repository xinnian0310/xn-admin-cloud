package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.AppConfigVO;
import com.smartadmin.entity.SysAppConfig;
import com.smartadmin.repository.SysAppConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppConfigService {

    private static final long SINGLETON_ID = 1L;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/webp", "image/svg+xml", "image/x-icon", "image/vnd.microsoft.icon");

    private final SysAppConfigRepository repository;
    private final RbacService rbacService;
    private final ObjectMapper objectMapper;
    private final AppCacheService appCacheService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /** 公开读取（登录页品牌等），无需鉴权 */
    public AppConfigVO getPublic() {
        return appCacheService.getAppConfig(new tools.jackson.core.type.TypeReference<>() {
        }, this::loadOrDefault);
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
        SysAppConfig entity = repository.findById(SINGLETON_ID).orElseGet(() -> {
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
        return normalize(repository.findById(SINGLETON_ID)
                .map(this::parse)
                .orElseGet(AppConfigVO::new));
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

    private AppConfigVO normalize(AppConfigVO vo) {
        if (vo.getLogRetention() == null) {
            vo.setLogRetention(new AppConfigVO.LogRetentionConfig());
        }
        vo.setSensitiveData(normalizeSensitiveData(vo.getSensitiveData()));
        return vo;
    }

    /** 供业务读取脱敏策略（走缓存公开配置） */
    public AppConfigVO.SensitiveDataConfig resolveSensitiveData() {
        return normalize(getPublic()).getSensitiveData();
    }

    private AppConfigVO.SensitiveDataConfig normalizeSensitiveData(AppConfigVO.SensitiveDataConfig cfg) {
        if (cfg == null) {
            cfg = new AppConfigVO.SensitiveDataConfig();
        }
        if (cfg.getEnabled() == null) {
            cfg.setEnabled(true);
        }
        if (cfg.getFields() == null || cfg.getFields().isEmpty()) {
            cfg.setFields(new java.util.ArrayList<>(java.util.List.of("phone", "email")));
        } else {
            java.util.List<String> cleaned = cfg.getFields().stream()
                    .filter(f -> f != null && !f.isBlank())
                    .map(f -> f.trim().toLowerCase())
                    .filter(ALLOWED_SENSITIVE_FIELDS::contains)
                    .distinct()
                    .toList();
            cfg.setFields(new java.util.ArrayList<>(cleaned.isEmpty()
                    ? java.util.List.of("phone", "email")
                    : cleaned));
        }
        return cfg;
    }

    private static final java.util.Set<String> ALLOWED_SENSITIVE_FIELDS = java.util.Set.of("phone", "email");

    private void validate(AppConfigVO vo) {
        if (vo.getApp() == null || !StringUtils.hasText(vo.getApp().getName())) {
            throw new BusinessException("项目名称不能为空");
        }
        if (vo.getUi() != null && vo.getUi().getLayout() != null) {
            String mode = vo.getUi().getLayout().getMode();
            if (StringUtils.hasText(mode)
                    && !Set.of("side", "top", "mix", "columns").contains(mode)) {
                throw new BusinessException("布局模式无效");
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
}
