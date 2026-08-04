package com.smartadmin.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class MenuPermissionGroupVO {

    private Long menuId;
    private String menuName;
    private String menuCode;
    private List<PermissionVO> api = new ArrayList<>();
    private List<PermissionVO> button = new ArrayList<>();
    private List<PermissionVO> tableButton = new ArrayList<>();
}
