package com.smartadmin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TableCodegenRequest {

    @NotBlank(message = "请选择数据表")
    private String tableName;

    @NotBlank(message = "请填写模块前缀")
    private String modulePrefix;

    /** Java 类名，如 Order；空则由前缀推导 */
    private String className;

    @NotBlank(message = "请填写 API 路径")
    private String apiBasePath;

    @NotBlank(message = "请填写菜单标题")
    private String menuTitle;

    @NotBlank(message = "请填写菜单路径")
    private String menuPath;

    @NotBlank(message = "请填写视图目录")
    private String viewPath;

    private Boolean persistPermissions = true;
    private Boolean generatePageUi = true;
    /** 是否创建菜单路由（不存在时） */
    private Boolean createMenu = true;

    @NotEmpty(message = "请至少配置一列")
    @Valid
    private List<TableCodegenColumnRequest> columns = new ArrayList<>();

    @Data
    public static class TableCodegenColumnRequest {
        @NotBlank
        private String columnName;
        private String label;
        private String javaType;
        private String javaField;
        private String formType;
        private boolean pk;
        private boolean nullable = true;
        private Integer columnSize;
        private boolean listShow = true;
        private boolean queryable;
        private boolean formShow = true;
        private boolean required;
    }
}
