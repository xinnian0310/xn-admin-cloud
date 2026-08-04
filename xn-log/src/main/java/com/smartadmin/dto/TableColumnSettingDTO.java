package com.smartadmin.dto;

import lombok.Data;

@Data
public class TableColumnSettingDTO {

    /** 列唯一键：prop / slot / type */
    private String key;

    /** 字段值（prop） */
    private String prop;

    /** 列名 */
    private String label;

    /** 宽度 */
    private Integer width;

    /** 是否显示 */
    private Boolean visible = true;

    /** 排序序号 */
    private Integer sort = 0;
}
