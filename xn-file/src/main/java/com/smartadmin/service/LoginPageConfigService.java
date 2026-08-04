package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.LoginPageConfigRequest;
import com.smartadmin.dto.LoginPageConfigVO;
import com.smartadmin.dto.PageResult;
import com.smartadmin.entity.SysLoginPageConfig;
import com.smartadmin.repository.SysLoginPageConfigRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class LoginPageConfigService {

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml");

    private final SysLoginPageConfigRepository repository;
    private final RbacService rbacService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /** 公开接口：当前启用的登录页配置（无需登录） */
    public LoginPageConfigVO getActive() {
        return repository.findFirstByStatus(1).map(LoginPageConfigVO::from).orElse(null);
    }

    public PageResult<LoginPageConfigVO> list(int page, int size, String keyword, Integer status) {
        rbacService.checkPermission("login-page:view");
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<SysLoginPageConfig> result =
                repository.search(
                        StringUtils.hasText(keyword) ? keyword.trim() : "", status, pageable);
        List<LoginPageConfigVO> records =
                result.getContent().stream().map(LoginPageConfigVO::from).toList();
        return new PageResult<>(records, result.getTotalElements(), page, size);
    }

    public LoginPageConfigVO getById(Long id) {
        rbacService.checkPermission("login-page:view");
        return LoginPageConfigVO.from(findConfig(id));
    }

    @Transactional
    public LoginPageConfigVO create(LoginPageConfigRequest request) {
        rbacService.checkPermission("login-page:create");
        validateCaptcha(request);
        SysLoginPageConfig config = new SysLoginPageConfig();
        applyRequest(config, request);
        if (Integer.valueOf(1).equals(config.getStatus())) {
            repository.disableAllExcept(null);
        }
        return LoginPageConfigVO.from(repository.save(config));
    }

    @Transactional
    public LoginPageConfigVO update(Long id, LoginPageConfigRequest request) {
        rbacService.checkPermission("login-page:update");
        validateCaptcha(request);
        SysLoginPageConfig config = findConfig(id);
        applyRequest(config, request);
        if (Integer.valueOf(1).equals(config.getStatus())) {
            repository.disableAllExcept(id);
        }
        return LoginPageConfigVO.from(repository.save(config));
    }

    @Transactional
    public void updateStatus(Long id, Integer status) {
        rbacService.checkPermission("login-page:update");
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值无效");
        }
        SysLoginPageConfig config = findConfig(id);
        if (status == 1) {
            repository.disableAllExcept(id);
        }
        config.setStatus(status);
        repository.save(config);
    }

    @Transactional
    public void delete(Long id) {
        rbacService.checkPermission("login-page:delete");
        repository.delete(findConfig(id));
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        rbacService.checkPermission("login-page:delete");
        int count = 0;
        for (Long id : ids) {
            repository.delete(findConfig(id));
            count++;
        }
        return count;
    }

    /** 上传登录页背景图，返回可公开访问的 URL */
    public String uploadBackground(MultipartFile file) {
        if (!rbacService.hasPermission("login-page:create")
                && !rbacService.hasPermission("login-page:update")) {
            throw new BusinessException(403, "无权限");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择图片文件");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new BusinessException("仅支持 jpg/png/gif/webp/svg 图片");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException("图片大小不能超过 5MB");
        }

        String ext = resolveExtension(file.getOriginalFilename(), contentType);
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        Path dir = Paths.get(uploadDir, "login").toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.transferTo(target);
        } catch (IOException e) {
            throw new BusinessException("图片上传失败");
        }
        return "/uploads/login/" + filename;
    }

    private void validateCaptcha(LoginPageConfigRequest request) {
        if (Boolean.TRUE.equals(request.getCaptchaEnabled())) {
            if (!StringUtils.hasText(request.getCaptchaType())) {
                throw new BusinessException("开启验证时请选择验证类型");
            }
            String type = request.getCaptchaType().trim().toUpperCase();
            if (!"IMAGE".equals(type) && !"SLIDER".equals(type)) {
                throw new BusinessException("验证类型仅支持 IMAGE 或 SLIDER");
            }
            request.setCaptchaType(type);
        } else {
            request.setCaptchaType(null);
        }
    }

    private String normalizeBackgroundFit(String fit) {
        if (!StringUtils.hasText(fit)) {
            return "COVER";
        }
        String value = fit.trim().toUpperCase();
        if (!Set.of("COVER", "CONTAIN", "STRETCH", "CENTER").contains(value)) {
            throw new BusinessException("背景适应模式无效");
        }
        return value;
    }

    private void applyRequest(SysLoginPageConfig config, LoginPageConfigRequest request) {
        config.setName(request.getName().trim());
        config.setBackgroundUrl(
                StringUtils.hasText(request.getBackgroundUrl())
                        ? request.getBackgroundUrl().trim()
                        : null);
        config.setBackgroundFit(normalizeBackgroundFit(request.getBackgroundFit()));
        // 任一为空则视为默认居中（两端都清空）
        if (request.getBoxX() == null || request.getBoxY() == null) {
            config.setBoxX(null);
            config.setBoxY(null);
        } else {
            config.setBoxX(request.getBoxX());
            config.setBoxY(request.getBoxY());
        }
        config.setCaptchaEnabled(Boolean.TRUE.equals(request.getCaptchaEnabled()));
        config.setCaptchaType(request.getCaptchaType());
        config.setStatus(request.getStatus() != null ? request.getStatus() : 0);
        config.setRemark(request.getRemark());
    }

    private SysLoginPageConfig findConfig(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException("登录页配置不存在"));
    }

    private String resolveExtension(String originalFilename, String contentType) {
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            String ext =
                    originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase();
            if (ext.matches("\\.(jpe?g|png|gif|webp|svg)")) {
                return ext.equals(".jpeg") ? ".jpg" : ext;
            }
        }
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/svg+xml" -> ".svg";
            default -> ".jpg";
        };
    }
}
