package com.smartadmin.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class RouteCodegenVO {

    private Long routeId;
    private String routePath;
    private String viewPath;
    private String modulePrefix;
    private String apiBasePath;
    private String template;

    /** 已落库或计划生成的权限码 */
    private List<String> permissionCodes = new ArrayList<>();

    /** 本次新写入权限表的数量 */
    private int persistedPermissionCount;

    /** 是否写入了 PageUi */
    private boolean pageUiPersisted;

    private String sql;

    private List<GeneratedFile> files = new ArrayList<>();

    /** 全部文件 + SQL 的 zip（Base64），便于一键下载 */
    private String zipBase64;

    @Data
    public static class GeneratedFile {
        private String path;
        private String content;

        public static GeneratedFile of(String path, String content) {
            GeneratedFile file = new GeneratedFile();
            file.setPath(path);
            file.setContent(content);
            return file;
        }
    }
}
