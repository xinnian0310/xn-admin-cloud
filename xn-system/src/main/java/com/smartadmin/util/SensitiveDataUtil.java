package com.smartadmin.util;

import org.springframework.util.StringUtils;

/**
 * 手机号 / 邮箱脱敏。有 {@code user:sensitive:view} 权限时不调用本工具。
 */
public final class SensitiveDataUtil {

    private SensitiveDataUtil() {
    }

    /** 是否为脱敏后的占位值（含 *），用于更新时跳过，避免掩码写回库 */
    public static boolean isMasked(String value) {
        return StringUtils.hasText(value) && value.indexOf('*') >= 0;
    }

    /** 手机号：保留前 3 后 4，中间打码；过短则全部打码 */
    public static String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return phone;
        }
        String s = phone.trim();
        if (isMasked(s)) {
            return s;
        }
        int len = s.length();
        if (len <= 4) {
            return "*".repeat(len);
        }
        if (len <= 7) {
            return s.substring(0, 1) + "*".repeat(len - 2) + s.substring(len - 1);
        }
        return s.substring(0, 3) + "*".repeat(len - 7) + s.substring(len - 4);
    }

    /** 邮箱：本地部分保留首尾，中间打码；@ 后域名保留 */
    public static String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return email;
        }
        String s = email.trim();
        if (isMasked(s)) {
            return s;
        }
        int at = s.indexOf('@');
        if (at <= 0) {
            return maskLocalPart(s);
        }
        return maskLocalPart(s.substring(0, at)) + s.substring(at);
    }

    private static String maskLocalPart(String local) {
        int len = local.length();
        if (len <= 1) {
            return "*";
        }
        if (len == 2) {
            return local.charAt(0) + "*";
        }
        return local.charAt(0) + "*".repeat(len - 2) + local.charAt(len - 1);
    }
}
