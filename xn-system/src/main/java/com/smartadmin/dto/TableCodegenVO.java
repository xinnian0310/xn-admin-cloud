package com.smartadmin.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TableCodegenVO {
    private String tableName;
    private String modulePrefix;
    private String className;
    private String apiBasePath;
    private String menuPath;
    private String viewPath;
    private List<String> permissionCodes = new ArrayList<>();
    private int persistedPermissionCount;
    private boolean pageUiPersisted;
    private boolean menuCreated;
    private String sql;
    private List<GeneratedFile> files = new ArrayList<>();
    private String zipBase64;

    @Data
    public static class GeneratedFile {
        private String path;
        private String content;

        public static GeneratedFile of(String path, String content) {
            GeneratedFile f = new GeneratedFile();
            f.setPath(path);
            f.setContent(content);
            return f;
        }
    }
}
