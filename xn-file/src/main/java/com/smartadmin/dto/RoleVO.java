package com.smartadmin.dto;

import com.smartadmin.entity.DataScope;
import com.smartadmin.entity.Role;
import lombok.Data;

@Data
public class RoleVO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer status;
    private Boolean builtIn;

    /** ALL | UNIT_AND_CHILDREN | UNIT | SELF */
    private String dataScope;

    public static RoleVO from(Role role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setCode(role.getCode());
        vo.setName(role.getName());
        vo.setDescription(role.getDescription());
        vo.setStatus(role.getStatus());
        vo.setBuiltIn(role.getBuiltIn());
        DataScope scope =
                role.getDataScope() != null ? role.getDataScope() : DataScope.UNIT_AND_CHILDREN;
        vo.setDataScope(scope.name());
        return vo;
    }
}
