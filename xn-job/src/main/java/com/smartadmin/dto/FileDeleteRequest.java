package com.smartadmin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FileDeleteRequest {

    @NotBlank(message = "文件路径不能为空")
    private String path;
}
