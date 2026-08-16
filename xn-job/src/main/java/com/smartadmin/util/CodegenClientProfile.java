package com.smartadmin.util;

import com.smartadmin.common.BusinessException;
import java.util.Locale;

/** 多前端栈代码生成配置。clientId 与前端仓库目录名一致。 */
public enum CodegenClientProfile {
    VUE3_TS("xn-admin-vue3-ts", "xn-admin-vue3-ts/src/", "views", ".ts", true, Stack.VUE3),
    VUE3_JS("xn-admin-vue3-js", "xn-admin-vue3-js/src/", "views", ".js", false, Stack.VUE3),
    VUE2_JS("xn-admin-vue2-js", "xn-admin-vue2-js/src/", "views", ".js", false, Stack.VUE2),
    REACT_TS("xn-admin-react-ts", "xn-admin-react-ts/src/", "pages", ".ts", true, Stack.REACT);

    public enum Stack {
        VUE3,
        VUE2,
        REACT
    }

    private final String clientId;
    private final String srcBase;
    private final String pageDir;
    private final String apiExt;
    private final boolean typed;
    private final Stack stack;

    CodegenClientProfile(
            String clientId,
            String srcBase,
            String pageDir,
            String apiExt,
            boolean typed,
            Stack stack) {
        this.clientId = clientId;
        this.srcBase = srcBase;
        this.pageDir = pageDir;
        this.apiExt = apiExt;
        this.typed = typed;
        this.stack = stack;
    }

    public String getClientId() {
        return clientId;
    }

    /** 如 xn-admin-vue3-ts/src/ */
    public String getSrcBase() {
        return srcBase;
    }

    /** views 或 pages */
    public String getPageDir() {
        return pageDir;
    }

    public String getApiExt() {
        return apiExt;
    }

    public boolean isTyped() {
        return typed;
    }

    public Stack getStack() {
        return stack;
    }

    public String pageFile(String viewPath, String name) {
        String ext = stack == Stack.REACT ? ".tsx" : ".vue";
        return srcBase + pageDir + "/" + trimSlashes(viewPath) + "/" + name + ext;
    }

    public String apiFile(String module) {
        return srcBase + "api/" + module + apiExt;
    }

    public static CodegenClientProfile fromClientId(String raw) {
        if (raw == null || raw.isBlank()) {
            return VUE3_TS;
        }
        String id = raw.trim().toLowerCase(Locale.ROOT);
        for (CodegenClientProfile p : values()) {
            if (p.clientId.equals(id)) {
                return p;
            }
        }
        throw new BusinessException(
                "未知 clientId: "
                        + raw
                        + "，允许值: xn-admin-vue3-ts / xn-admin-vue3-js / xn-admin-vue2-js / xn-admin-react-ts");
    }

    private static String trimSlashes(String path) {
        return path == null ? "" : path.replace('\\', '/').replaceAll("^/+|/+$", "");
    }
}
