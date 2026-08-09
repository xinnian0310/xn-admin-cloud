package com.smartadmin.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 接口守卫：
 *
 * <ol>
 *   <li>校验被调用接口是否已在「权限内容」中登记（未登记一律拦截）
 *   <li>游客角色（无超管/管理员叠加时）须拥有对应 api:* 权限码，用于「按钮可展示、写接口不可调」
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class ApiGuardInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiGuardInterceptor.class);

    /** 不受守卫约束的接口（登录、注册表本身） */
    private static final Set<String> WHITELIST =
            Set.of(
                    "/api/auth/login",
                    "/api/auth/logout",
                    "/api/auth/captcha",
                    "/api/auth/captcha/slider",
                    "/api/auth/api-registry",
                    "/api/login-page-configs/active",
                    "/api/system-config/public",
                    "/api/site-contact/public",
                    "/api/site-ui-shots/public");

    private final ApiPermissionRegistry registry;

    @Value("${app.api-guard.enforce:true}")
    private boolean enforce;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/api/") || WHITELIST.contains(uri)) {
            return true;
        }
        Optional<String> apiCode = registry.findCode(request.getMethod(), uri);
        if (apiCode.isEmpty()) {
            String message = "接口未在权限内容中登记，无法访问：" + request.getMethod() + " " + uri;
            log.warn("[api-guard] 拦截未登记接口：{} {}", request.getMethod(), uri);
            return deny(response, message);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (isGuestReadOnly(auth) && !hasAuthority(auth, apiCode.get())) {
            String message = "游客仅可访问已授权的查询类接口：" + request.getMethod() + " " + uri;
            log.warn("[api-guard] 拦截游客未授权接口：{} {} ({})", request.getMethod(), uri, apiCode.get());
            return deny(response, message);
        }
        return true;
    }

    /** 持有 GUEST 且未叠加 SUPER_ADMIN / ADMIN 时，按接口权限码收紧写操作 */
    private boolean isGuestReadOnly(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        boolean guest = false;
        boolean privileged = false;
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String code = authority.getAuthority();
            if ("ROLE_GUEST".equals(code)) {
                guest = true;
            } else if ("ROLE_SUPER_ADMIN".equals(code) || "ROLE_ADMIN".equals(code)) {
                privileged = true;
            }
        }
        return guest && !privileged;
    }

    private boolean hasAuthority(Authentication auth, String code) {
        if (auth == null || code == null) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (code.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private boolean deny(HttpServletResponse response, String message) throws Exception {
        if (!enforce) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.getWriter()
                .write("{\"code\":403,\"message\":\"" + escapeJson(message) + "\",\"data\":null}");
        return false;
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
