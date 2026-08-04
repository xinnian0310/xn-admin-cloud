package com.smartadmin.dto;

import com.smartadmin.entity.SysUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Data;

@Data
public class UnitVO {

    private Long id;
    private String code;
    private String name;
    private Long parentId;
    private String description;
    private Integer sort;
    private Integer status;
    private Boolean builtIn;
    private List<Long> roleIds = new ArrayList<>();
    private List<RoleVO> roleList = new ArrayList<>();
    private List<UnitVO> children = new ArrayList<>();

    public static UnitVO from(SysUnit unit) {
        UnitVO vo = new UnitVO();
        vo.setId(unit.getId());
        vo.setCode(unit.getCode());
        vo.setName(unit.getName());
        vo.setParentId(unit.getParentId());
        vo.setDescription(unit.getDescription());
        vo.setSort(unit.getSort() == null ? 0 : unit.getSort());
        vo.setStatus(unit.getStatus());
        vo.setBuiltIn(unit.getBuiltIn());
        if (unit.getRoles() != null && !unit.getRoles().isEmpty()) {
            vo.setRoleList(unit.getRoles().stream().map(RoleVO::from).collect(Collectors.toList()));
            vo.setRoleIds(
                    unit.getRoles().stream().map(r -> r.getId()).collect(Collectors.toList()));
        }
        return vo;
    }
}
