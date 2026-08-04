package com.smartadmin.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 从当前请求上下文提取客户端 IP / User-Agent，供登录日志、操作日志复用 */
public final class WebUtils {

    private WebUtils() {
    }

    public static HttpServletRequest getCurrentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    public static String getClientIp() {
        HttpServletRequest request = getCurrentRequest();
        return request == null ? "unknown" : getClientIp(request);
    }

    public static String getClientIp(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"
        };
        for (String header : headers) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value) && !"unknown".equalsIgnoreCase(value)) {
                return normalizeIp(value.split(",")[0].trim());
            }
        }
        return normalizeIp(request.getRemoteAddr());
    }

    /** 本机 IPv6 回环统一显示为 127.0.0.1，便于阅读 */
    private static String normalizeIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return "unknown";
        }
        String trimmed = ip.trim();
        // ::1 及其常见展开写法均为本机回环
        if ("::1".equals(trimmed) || "0:0:0:0:0:0:0:1".equals(trimmed)) {
            return "127.0.0.1";
        }
        return trimmed;
    }

    public static String getUserAgent() {
        HttpServletRequest request = getCurrentRequest();
        return request == null ? null : request.getHeader("User-Agent");
    }
}
