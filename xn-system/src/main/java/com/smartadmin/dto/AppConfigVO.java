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
    private SensitiveDataConfig sensitiveData = new SensitiveDataConfig();

    @Getter
    @Setter
    public static class AppInfo {
        private String name = "心念后台管理系统";
        private String company = "心念科技";
        private String subtitle = "心念科技";
        private String favicon = "/favicon.svg";
        private String logo = "/logo.svg";
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
