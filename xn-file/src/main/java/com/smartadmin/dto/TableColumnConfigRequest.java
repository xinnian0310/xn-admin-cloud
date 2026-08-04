package com.smartadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TableColumnConfigRequest {

    @NotBlank
    private String tableKey;

    @NotEmpty
    private List<TableColumnSettingDTO> columns = new ArrayList<>();
}
