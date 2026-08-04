package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.LoginPageConfigRequest;
import com.smartadmin.dto.LoginPageConfigVO;
import com.smartadmin.dto.PageResult;
import com.smartadmin.entity.SysLoginPageConfig;
import com.smartadmin.repository.SysLoginPageConfigRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class LoginPageConfigService {

    private final SysLoginPageConfigRepository repository;
    private final RbacService rbacService;

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

    private void applyRequest(SysLoginPageConfig config, LoginPageConfigRequest request) {
        config.setName(request.getName().trim());
        // 布局已由前端固定品牌页承担，不再接受后台背景 / 位置配置
        config.setBackgroundUrl(null);
        config.setBackgroundFit("COVER");
        config.setBoxX(null);
        config.setBoxY(null);
        config.setCaptchaEnabled(Boolean.TRUE.equals(request.getCaptchaEnabled()));
        config.setCaptchaType(request.getCaptchaType());
        config.setStatus(request.getStatus() != null ? request.getStatus() : 0);
        config.setRemark(request.getRemark());
    }

    private SysLoginPageConfig findConfig(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException("登录页配置不存在"));
    }
}
