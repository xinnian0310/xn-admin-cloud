package com.smartadmin.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class PageUiSearchItemDTO {

    private String label;
    private String prop;
    private String type;
    private String placeholder;
    private String permission;
    private String width;
    private Boolean clearable;
    private Boolean multiple;
    private List<PageUiOptionDTO> options = new ArrayList<>();
}
