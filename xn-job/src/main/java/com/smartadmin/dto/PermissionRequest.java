package com.smartadmin.dto;

import com.smartadmin.entity.PermissionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PermissionRequest {

    @NotBlank(message = "权限编码不能为空")
    @Size(max = 100, message = "权限编码长度不能超过100")
    private String code;

    @NotBlank(message = "权限名称不能为空")
    @Size(max = 100, message = "权限名称长度不能超过100")
    private String name;

    @NotNull(message = "权限类型不能为空")
    private PermissionType type;

    private Long parentId;

    @Size(max = 200, message = "路径长度不能超过200")
    private String path;

    @Size(max = 10, message = "方法长度不能超过10")
    private String method;

    @Size(max = 50, message = "动作标识长度不能超过50")
    private String action;

    @Size(max = 50, message = "图标长度不能超过50")
    private String icon;

    @Size(max = 20, message = "按钮颜色长度不能超过20")
    private String buttonColor;

    private Integer sort = 0;
}
