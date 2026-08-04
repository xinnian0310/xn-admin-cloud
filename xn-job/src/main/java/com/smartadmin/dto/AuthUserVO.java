package com.smartadmin.dto;

import com.smartadmin.entity.User;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AuthUserVO extends UserVO {

    private List<String> roles;
    private List<String> permissions;

    /** 需强制修改密码（首次/管理员重置/已过期） */
    private Boolean mustChangePassword;

    public static AuthUserVO from(User user, List<String> roleCodes, List<String> permissionCodes) {
        return from(user, roleCodes, permissionCodes, false);
    }

    public static AuthUserVO from(
            User user,
            List<String> roleCodes,
            List<String> permissionCodes,
            boolean mustChangePassword) {
        AuthUserVO vo = new AuthUserVO();
        UserVO base = UserVO.from(user);
        vo.setId(base.getId());
        vo.setUsername(base.getUsername());
        vo.setNickname(base.getNickname());
        vo.setEmail(base.getEmail());
        vo.setPhone(base.getPhone());
        vo.setStatus(base.getStatus());
        vo.setRole(base.getRole());
        vo.setCreatedAt(base.getCreatedAt());
        vo.setUpdatedAt(base.getUpdatedAt());
        vo.setRoleList(base.getRoleList());
        vo.setUnitId(base.getUnitId());
        vo.setUnitName(base.getUnitName());
        vo.setPostId(base.getPostId());
        vo.setPostName(base.getPostName());
        vo.setAvatar(base.getAvatar());
        vo.setRoles(roleCodes);
        vo.setPermissions(permissionCodes);
        vo.setMustChangePassword(mustChangePassword);
        return vo;
    }
}
