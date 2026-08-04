package com.smartadmin.dto;

import lombok.Data;

@Data
public class PostImportRow {

    private String code;
    private String name;
    private Integer sort;
    private Integer status;
    private String remark;
}
