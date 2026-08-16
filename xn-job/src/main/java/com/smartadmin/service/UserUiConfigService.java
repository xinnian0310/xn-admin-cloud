package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.UserUiConfigVO;
import com.smartadmin.entity.SysUserUiConfig;
import com.smartadmin.entity.User;
import com.smartadmin.repository.SysUserUiConfigRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class UserUiConfigService {

    private static final Set<String> LAYOUT_MODES = Set.of("side", "top", "mix", "columns");

    private final SysUserUiConfigRepository repository;
    private final RbacService rbacService;
    private final ObjectMapper objectMapper;

    public UserUiConfigVO getForCurrentUser() {
        User user = rbacService.currentUser();
        return repository.findByUserId(user.getId()).map(this::parse).orElse(null);
    }

    @Transactional
    public UserUiConfigVO saveForCurrentUser(UserUiConfigVO request) {
        if (request == null) {
            throw new BusinessException("配置不能为空");
        }
        validate(request);
        User user = rbacService.currentUser();
        SysUserUiConfig entity =
                repository.findByUserId(user.getId()).orElseGet(SysUserUiConfig::new);
        entity.setUserId(user.getId());
        try {
            entity.setConfigJson(objectMapper.writeValueAsString(request));
        } catch (Exception e) {
            throw new BusinessException("配置序列化失败");
        }
        repository.save(entity);
        return request;
    }

    @Transactional
    public void resetForCurrentUser() {
        User user = rbacService.currentUser();
        repository.deleteByUserId(user.getId());
    }

    private void validate(UserUiConfigVO request) {
        if (request.getLayout() != null && StringUtils.hasText(request.getLayout().getMode())) {
            String mode = request.getLayout().getMode().trim();
            if (!LAYOUT_MODES.contains(mode)) {
                throw new BusinessException("布局模式无效");
            }
            request.getLayout().setMode(mode);
        }
        if (request.getFontSize() != null) {
            request.getFontSize()
                    .setSidebar(normalizePx(request.getFontSize().getSidebar(), "侧栏字号"));
            request.getFontSize().setHeader(normalizePx(request.getFontSize().getHeader(), "顶栏字号"));
            request.getFontSize()
                    .setTagsView(normalizePx(request.getFontSize().getTagsView(), "标签栏字号"));
            request.getFontSize().setMain(normalizePx(request.getFontSize().getMain(), "正文字号"));
        }
        if (request.getTagsView() != null) {
            request.getTagsView()
                    .setHeight(normalizePx(request.getTagsView().getHeight(), "标签栏高度"));
        }
    }

    /** 允许空；非空则必须为正整数，并统一成 Npx */
    private String normalizePx(String raw, String label) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim().toLowerCase().replace("px", "").trim();
        if (!text.matches("^[1-9]\\d*$")) {
            throw new BusinessException(label + "须为正整数（单位 px）");
        }
        return text + "px";
    }

    private UserUiConfigVO parse(SysUserUiConfig entity) {
        try {
            return objectMapper.readValue(entity.getConfigJson(), UserUiConfigVO.class);
        } catch (Exception e) {
            throw new BusinessException("个人配置解析失败");
        }
    }
}
