package com.smartadmin.dto;

import lombok.Data;

@Data
public class ColumnMetaVO {
    private String columnName;
    private String remarks;
    private String label;
    private String jdbcType;
    private String javaType;
    private String javaField;

    /** input / number / select / datetime / textarea */
    private String formType;

    private boolean pk;
    private boolean nullable;
    private Integer columnSize;
    private boolean listShow;
    private boolean queryable;
    private boolean formShow;
    private boolean required;
}
