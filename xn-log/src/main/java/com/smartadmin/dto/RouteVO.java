package com.smartadmin.dto;

import com.smartadmin.entity.RouteType;
import com.smartadmin.entity.SysRoute;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Data;

@Data
public class RouteVO {

    private Long id;
    private String title;
    private String path;
    private String viewPath;
    private String icon;
    private String permission;
    private Long parentId;
    private RouteType type;
    private Integer sort;
    private Integer status;
    private Boolean hidden;
    private Boolean affix;
    private Boolean permissionControl;
    private Boolean builtIn;
    private List<RouteVO> children = new ArrayList<>();

    public static RouteVO from(SysRoute route) {
        RouteVO vo = new RouteVO();
        vo.setId(route.getId());
        vo.setTitle(route.getTitle());
        vo.setPath(route.getPath());
        vo.setViewPath(route.getViewPath());
        vo.setIcon(route.getIcon());
        vo.setPermission(route.getPermission());
        vo.setParentId(route.getParent() != null ? route.getParent().getId() : null);
        vo.setType(route.getType());
        vo.setSort(route.getSort());
        vo.setStatus(route.getStatus());
        vo.setHidden(route.getHidden());
        vo.setAffix(route.getAffix());
        vo.setPermissionControl(Boolean.TRUE.equals(route.getPermissionControl()));
        vo.setBuiltIn(route.getBuiltIn());
        return vo;
    }

    public static RouteVO treeFrom(SysRoute route) {
        RouteVO vo = from(route);
        if (route.getChildren() != null && !route.getChildren().isEmpty()) {
            vo.setChildren(
                    route.getChildren().stream()
                            .filter(r -> r.getStatus() == 1 && !Boolean.TRUE.equals(r.getHidden()))
                            .sorted(Comparator.comparing(SysRoute::getSort))
                            .map(RouteVO::treeFrom)
                            .toList());
        }
        return vo;
    }
}
