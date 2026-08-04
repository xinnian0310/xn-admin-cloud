package com.smartadmin.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TableColumnConfigVO {

    private String tableKey;
    private List<TableColumnSettingDTO> columns = new ArrayList<>();
}
