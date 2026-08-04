package com.smartadmin.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 接口守卫：校验被调用接口是否已在「权限内容」中登记。 未登记一律拦截（app.api-guard.enforce=true，默认开启）。 */
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
                    "/api/system-config/public");

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
        if (registry.isRegistered(request.getMethod(), uri)) {
            return true;
        }

        String message = "接口未在权限内容中登记，无法访问：" + request.getMethod() + " " + uri;
        log.warn("[api-guard] 拦截未登记接口：{} {}", request.getMethod(), uri);
        if (!enforce) {
            // 仅应急调试可临时关掉 enforce；默认必须拦截
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
