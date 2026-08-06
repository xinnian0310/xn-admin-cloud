package com.smartadmin.dto;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** 与前端 {@code app.ts / app.js} 的 appConfig 同构，供系统配置读写与公开下发。 */
@Getter
@Setter
public class AppConfigVO {

    private AppInfo app = new AppInfo();
    private SessionConfig session = new SessionConfig();
    private UiConfig ui = new UiConfig();
    private StorageConfig storage = new StorageConfig();
    private LogRetentionConfig logRetention = new LogRetentionConfig();
    private SensitiveDataConfig sensitiveData = new SensitiveDataConfig();

    /** 单个前端工程的品牌文案（项目名称 / 应用介绍）。 多前端共用一套后端时按 clientId 隔离。 */
    @Getter
    @Setter
    public static class ClientAppProfile {
        private String name;
        private String intro;
    }

    @Getter
    @Setter
    public static class AppInfo {
        /** 兜底名称：未知 client 或未配置 clients 时使用 */
        private String name = "心念后台管理系统";

        /** 根级介绍留空；各前端介绍只存 {@link #clients}，公开接口按 clientId 投影到此字段下发。 */
        private String intro = "";

        /**
         * 按前端工程隔离的名称 / 介绍。key 为稳定 clientId（与前端约定一致）。
         * 已知：xn-admin-vue3-ts、xn-admin-vue3-js、xn-admin-vue2-js、xn-admin-react-ts
         */
        private Map<String, ClientAppProfile> clients = defaultClientProfiles();

        private String favicon = "/xinnian-tech-logo.png";
        private String logo = "/xinnian-tech-logo.png";
        private Integer logoWidth = 28;
        private Integer logoHeight;
        private String footer = "心念后台管理系统 · 心念科技 · Copyright © 2026";

        /** 各技术栈默认名称 / 介绍；已存在的 key 不会被覆盖。 */
        public static Map<String, ClientAppProfile> defaultClientProfiles() {
            Map<String, ClientAppProfile> map = new LinkedHashMap<>();
            map.put(
                    "xn-admin-vue3-ts",
                    clientProfile(
                            "心念后台管理系统（Vue3 TS）",
                            "面向中后台的 Vue3 + TypeScript 微服务管理脚手架：JWT 登录、RBAC 动态路由、page-ui 驱动 CRUD、多布局与主题、通知推送与系统监控一站集成，对接 xn-admin-cloud 网关即可开箱使用。推荐生产与长期维护选型。"));
            map.put(
                    "xn-admin-vue3-js",
                    clientProfile(
                            "心念后台管理系统（Vue3 JS）",
                            "面向中后台的 Vue3 + JavaScript 微服务管理脚手架：JWT 登录、RBAC 动态路由、page-ui 驱动 CRUD、多布局与主题、通知推送与系统监控一站集成，对接 xn-admin-cloud 网关即可开箱使用。"));
            map.put(
                    "xn-admin-vue2-js",
                    clientProfile(
                            "心念后台管理系统（Vue2 JS）",
                            "面向中后台的 Vue2 + JavaScript 微服务管理脚手架：JWT 登录、RBAC 动态路由、page-ui 驱动 CRUD、多布局与主题、通知推送与系统监控一站集成，对接 xn-admin-cloud 网关即可开箱使用。"));
            map.put(
                    "xn-admin-react-ts",
                    clientProfile(
                            "心念后台管理系统（React）",
                            "面向中后台的 React 19 + TypeScript + Ant Design 微服务管理脚手架：JWT 登录、RBAC 动态路由、page-ui 驱动 CRUD、多布局与主题、通知推送与系统监控一站集成，对接 xn-admin-cloud 网关即可开箱使用。"));
            return map;
        }

        private static ClientAppProfile clientProfile(String name, String intro) {
            ClientAppProfile profile = new ClientAppProfile();
            profile.setName(name);
            profile.setIntro(intro);
            return profile;
        }
    }

    @Getter
    @Setter
    public static class SessionConfig {
        private Boolean idleLogoutEnabled = true;
        private Long idleTimeoutMs = 30L * 60 * 1000;
        private Boolean slidingRefreshEnabled = true;
        private Long refreshIntervalMs = 5L * 60 * 1000;
        private Long idleCheckIntervalMs = 30L * 1000;
    }

    @Getter
    @Setter
    public static class UiConfig {
        private DialogUi dialog = new DialogUi();
        private LayoutUi layout = new LayoutUi();
        private FontSizeUi fontSize = new FontSizeUi();
        private TagsViewUi tagsView = new TagsViewUi();
        private ElementPlusUi elementPlus = new ElementPlusUi();
    }

    @Getter
    @Setter
    public static class DialogUi {
        private String maxHeight = "95vh";
    }

    @Getter
    @Setter
    public static class LayoutUi {
        /** side | top | mix | columns */
        private String mode = "side";
    }

    @Getter
    @Setter
    public static class FontSizeUi {
        private String sidebar = "14px";
        private String header = "14px";
        private String tagsView = "14px";
        private String main = "14px";
    }

    @Getter
    @Setter
    public static class TagsViewUi {
        private String height = "40px";
    }

    @Getter
    @Setter
    public static class ElementPlusUi {
        private String locale = "zh-cn";
        private String size = "default";
        private Integer zIndex = 2000;
        private String namespace = "el";
        private ButtonUi button = new ButtonUi();
        private MessageUi message = new MessageUi();
        private EpDialogUi dialog = new EpDialogUi();
    }

    @Getter
    @Setter
    public static class ButtonUi {
        private Boolean autoInsertSpace = false;
    }

    @Getter
    @Setter
    public static class MessageUi {
        private Integer max = 3;
    }

    @Getter
    @Setter
    public static class EpDialogUi {
        private Boolean alignCenter = true;
        private Boolean draggable = true;
        private Boolean overflow = false;
    }

    @Getter
    @Setter
    public static class StorageConfig {
        private MinioConfig minio = new MinioConfig();
    }

    @Getter
    @Setter
    public static class MinioConfig {
        private String endpoint = "";
        private String bucket = "";
        private String region = "";
    }

    /** 日志保留天数；定时任务按此清理过期日志。0 或负数表示不清理。 */
    @Getter
    @Setter
    public static class LogRetentionConfig {
        private Integer loginDays = 90;
        private Integer operDays = 90;
        private Integer exceptionDays = 90;
        private Integer jobDays = 90;
    }

    /** 用户敏感字段脱敏：无 {@code user:sensitive:view} 时对勾选字段打码。 fields 仅支持 phone / email。 */
    @Getter
    @Setter
    public static class SensitiveDataConfig {
        private Boolean enabled = true;
        private java.util.List<String> fields =
                new java.util.ArrayList<>(java.util.List.of("phone", "email"));
    }
}
