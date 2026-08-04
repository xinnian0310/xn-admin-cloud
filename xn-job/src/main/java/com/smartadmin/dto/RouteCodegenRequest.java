package com.smartadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RouteCodegenRequest {

    /**
     * 页面模板：BLANK 空白页 / LIST 仅列表 / CRUD 标准增删改查
     */
    @NotNull(message = "请选择页面模板")
    private Template template = Template.CRUD;

    /** 权限码/按钮前缀，如 order、dict-type */
    @NotBlank(message = "请填写模块前缀")
    private String modulePrefix;

    /** REST 路径前缀，如 /api/orders */
    @NotBlank(message = "请填写 API 路径")
    private String apiBasePath;

    /** 是否将按钮/接口权限写入数据库（默认 true） */
    private Boolean persistPermissions = true;

    /** 是否写入 PageUi 搜索配置（默认 true；BLANK 忽略） */
    private Boolean generatePageUi = true;

    public enum Template {
        BLANK,
        LIST,
        CRUD
    }
}
