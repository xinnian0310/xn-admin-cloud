package com.smartadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class TableColumnConfigRequest {

    @NotBlank private String tableKey;

    @NotEmpty private List<TableColumnSettingDTO> columns = new ArrayList<>();
}
