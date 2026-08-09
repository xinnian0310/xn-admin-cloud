package com.smartadmin.dto;

import lombok.Getter;
import lombok.Setter;

/** 与前端 {@code app.ts} 的 appConfig 同构，供系统配置读写与公开下发。 */
@Getter
@Setter
public class AppConfigVO {

    private AppInfo app = new AppInfo();
    private SessionConfig session = new SessionConfig();
    private UiConfig ui = new UiConfig();
    private StorageConfig storage = new StorageConfig();
    private LogRetentionConfig logRetention = new LogRetentionConfig();

    @Getter
    @Setter
    public static class AppInfo {
        private String name = "心念后台管理系统";

        /** 应用介绍：管理端首页 / 官网开源项目介绍 */
        private String intro =
                "面向中后台的 Vue3 + 微服务管理脚手架：JWT 登录、RBAC 动态路由、page-ui 驱动 CRUD、多布局与主题、通知推送与系统监控一站集成，对接 xn-admin-cloud 网关即可开箱使用。";

        private String favicon = "/xinnian-tech-logo.png";
        private String logo = "/xinnian-tech-logo.png";
        private Integer logoWidth = 28;
        private Integer logoHeight;
        private String footer = "心念后台管理系统 · 心念科技 · Copyright © 2026";
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
        private AntdUi antd = new AntdUi();
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
    public static class AntdUi {
        private String locale = "zh-cn";
        private String componentSize = "middle";
        private String prefixCls = "ant";
        private ButtonUi button = new ButtonUi();
        private AntdMessageUi message = new AntdMessageUi();
        private AntdModalUi modal = new AntdModalUi();
    }

    @Getter
    @Setter
    public static class AntdMessageUi {
        private Integer maxCount = 3;
    }

    @Getter
    @Setter
    public static class AntdModalUi {
        private Boolean centered = true;

        /** React XnModal 可拖拽；与 Element Plus dialog.draggable 语义对齐 */
        private Boolean draggable = true;
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
}
