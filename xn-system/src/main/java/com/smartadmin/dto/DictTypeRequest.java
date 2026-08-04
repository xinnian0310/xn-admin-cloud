package com.smartadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DictTypeRequest {

    @NotBlank(message = "字典名称不能为空")
    @Size(max = 50, message = "字典名称长度不能超过50")
    private String name;

    @NotBlank(message = "字典类型编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "字典类型编码需以小写字母开头，只能包含小写字母、数字、下划线")
    @Size(max = 100, message = "字典类型编码长度不能超过100")
    private String type;

    private Integer status = 1;

    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
