package com.smartadmin.dto;

import com.smartadmin.entity.Permission;
import com.smartadmin.entity.PermissionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Data;

@Data
public class PermissionVO {

    private Long id;
    private String code;
    private String name;
    private PermissionType type;
    private Long parentId;
    private String path;
    private String method;
    private String action;
    private String icon;
    private String iconAntd;
    private String buttonColor;
    private Integer sort;
    private Boolean builtIn;
    private List<PermissionVO> children = new ArrayList<>();

    public static PermissionVO from(Permission permission) {
        PermissionVO vo = new PermissionVO();
        vo.setId(permission.getId());
        vo.setCode(permission.getCode());
        vo.setName(permission.getName());
        vo.setType(permission.getType());
        vo.setParentId(permission.getParent() != null ? permission.getParent().getId() : null);
        vo.setPath(permission.getPath());
        vo.setMethod(permission.getMethod());
        vo.setAction(permission.getAction());
        vo.setIcon(permission.getIcon());
        vo.setIconAntd(permission.getIconAntd());
        vo.setButtonColor(permission.getButtonColor());
        vo.setSort(permission.getSort());
        vo.setBuiltIn(permission.getBuiltIn());
        return vo;
    }

    public static PermissionVO treeFrom(Permission permission) {
        PermissionVO vo = from(permission);
        if (permission.getChildren() != null && !permission.getChildren().isEmpty()) {
            vo.setChildren(
                    permission.getChildren().stream()
                            .sorted(Comparator.comparing(Permission::getSort))
                            .map(PermissionVO::treeFrom)
                            .toList());
        }
        return vo;
    }
}
