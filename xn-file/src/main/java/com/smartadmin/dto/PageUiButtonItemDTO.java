package com.smartadmin.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PageUiButtonItemDTO {

    private String name;
    /** 前端动作标识：add / edit / view / delete */
    private String action;
    private String type = "button";
    private String icon;
    private String typeColor;
    private String permission;
    private Integer index;
    private Boolean disabled;
    private List<PageUiButtonDropdownDTO> searchItem = new ArrayList<>();
}
