package com.smartadmin.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class TableColumnConfigVO {

    private String tableKey;
    private List<TableColumnSettingDTO> columns = new ArrayList<>();
}
