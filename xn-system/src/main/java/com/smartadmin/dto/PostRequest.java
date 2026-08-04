package com.smartadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostRequest {

    @NotBlank(message = "岗位编码不能为空")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", message = "岗位编码需以字母开头，只能包含字母、数字、下划线")
    @Size(max = 50, message = "岗位编码长度不能超过50")
    private String code;

    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 50, message = "岗位名称长度不能超过50")
    private String name;

    private Integer sort = 0;

    private Integer status = 1;

    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
