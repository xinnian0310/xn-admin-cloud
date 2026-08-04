package com.smartadmin.dto;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RoleDetailVO extends RoleVO {

    private List<Long> permissionIds;

    public static RoleDetailVO from(com.smartadmin.entity.Role role, List<Long> permissionIds) {
        RoleDetailVO vo = new RoleDetailVO();
        vo.setId(role.getId());
        vo.setCode(role.getCode());
        vo.setName(role.getName());
        vo.setDescription(role.getDescription());
        vo.setStatus(role.getStatus());
        vo.setBuiltIn(role.getBuiltIn());
        vo.setDataScope(RoleVO.from(role).getDataScope());
        vo.setPermissionIds(permissionIds);
        return vo;
    }
}
