package com.smartadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DictDataRequest {

    @NotBlank(message = "所属字典类型不能为空")
    @Size(max = 100, message = "字典类型编码长度不能超过100")
    private String dictType;

    @NotBlank(message = "字典标签不能为空")
    @Size(max = 100, message = "字典标签长度不能超过100")
    private String label;

    @NotBlank(message = "字典键值不能为空")
    @Size(max = 100, message = "字典键值长度不能超过100")
    private String value;

    private Integer sort = 0;

    private Integer status = 1;

    private Boolean isDefault = false;

    @Size(max = 20, message = "标签样式长度不能超过20")
    private String listClass;

    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
