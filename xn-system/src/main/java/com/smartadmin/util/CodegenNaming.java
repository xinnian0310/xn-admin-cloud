package com.smartadmin.util;

import com.smartadmin.common.BusinessException;
import org.springframework.util.StringUtils;

import java.util.Locale;

/** 代码生成命名工具（路由脚手架 / 表驱动共用）。 */
public final class CodegenNaming {

    private CodegenNaming() {}

    public static String normalizePrefix(String raw) {
        String v = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (!StringUtils.hasText(v)) {
            throw new BusinessException("模块前缀无效");
        }
        return v;
    }

    public static String normalizeApiBase(String raw) {
        String v = raw.trim().replace('\\', '/').replaceAll("/+", "/");
        if (v.endsWith("/") && v.length() > 1) {
            v = v.substring(0, v.length() - 1);
        }
        if (!v.startsWith("/")) {
            v = "/" + v;
        }
        if (!v.startsWith("/api/")) {
            v = "/api" + (v.startsWith("/") ? v : "/" + v);
        }
        return v;
    }

    public static String toPascal(String kebabOrSnake) {
        StringBuilder sb = new StringBuilder();
        for (String part : kebabOrSnake.split("[-_]")) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }

    public static String toCamel(String pascal) {
        if (!StringUtils.hasText(pascal)) return pascal;
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    /** 列名 user_name → userName */
    public static String columnToCamel(String column) {
        return toCamel(toPascal(column.replace('-', '_')));
    }

    public static String tableToPrefix(String tableName) {
        String t = tableName.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("biz_")) {
            t = t.substring(4);
        } else if (t.startsWith("sys_")) {
            t = t.substring(4);
        }
        return normalizePrefix(t.replace('_', '-'));
    }

    public static String defaultApiFromPrefix(String prefix) {
        return "/api/" + prefix;
    }

    public static String defaultMenuPath(String prefix) {
        return "/biz/" + prefix;
    }

    public static String defaultViewPath(String prefix) {
        return "biz/" + prefix;
    }
}
