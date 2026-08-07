package com.smartadmin.dto;

import com.smartadmin.entity.RouteType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RouteRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100)
    private String title;

    @Size(max = 200)
    private String path;

    @Size(max = 200)
    private String viewPath;

    /** 外链地址（LINK 类型） */
    @Size(max = 500)
    private String linkUrl;

    @Size(max = 50)
    private String icon;

    @Size(max = 100)
    private String permission;

    private Long parentId;

    @NotNull(message = "类型不能为空")
    private RouteType type;

    private Integer sort = 0;

    private Integer status = 1;

    private Boolean hidden = false;

    private Boolean affix = false;

    /** 是否启用菜单权限控制，默认关闭 */
    private Boolean permissionControl = false;
}
