package com.smartadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UnitRequest {

    @NotBlank(message = "单位编码不能为空")
    @Size(max = 50, message = "单位编码长度不能超过50")
    private String code;

    @NotBlank(message = "单位名称不能为空")
    @Size(max = 50, message = "单位名称长度不能超过50")
    private String name;

    private Long parentId;

    @Size(max = 200, message = "描述长度不能超过200")
    private String description;

    private Integer sort = 0;

    private Integer status = 1;

    /** 单位默认角色 */
    private List<Long> roleIds = new ArrayList<>();
}
