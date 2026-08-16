package com.smartadmin.dto;

import com.smartadmin.entity.Role;
import com.smartadmin.entity.User;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;

    /** 当前调用方看不到明文时为 true（列表/详情已打码） */
    private Boolean sensitiveMasked;

    private Integer status;

    /**
     * @deprecated 冗余，使用 roleList
     */
    private String role;

    /** 用户直接绑定的角色 */
    private List<RoleVO> roleList;

    /** 所属单位默认角色（继承） */
    private List<RoleVO> unitRoleList;

    /** 生效角色 = 个人 ∪ 单位 */
    private List<RoleVO> effectiveRoleList;

    private Long unitId;
    private String unitName;
    private Long postId;
    private String postName;
    private String avatar;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setStatus(user.getStatus());
        vo.setRole(user.getRole());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());
        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            vo.setRoleList(user.getRoles().stream().map(RoleVO::from).toList());
        } else {
            vo.setRoleList(List.of());
        }
        if (user.getUnit() != null) {
            vo.setUnitId(user.getUnit().getId());
            vo.setUnitName(user.getUnit().getName());
        }
        if (user.getPost() != null) {
            vo.setPostId(user.getPost().getId());
            vo.setPostName(user.getPost().getName());
        }
        return vo;
    }

    public void fillUnitRoles(List<Role> unitRoles) {
        if (unitRoles == null || unitRoles.isEmpty()) {
            setUnitRoleList(List.of());
        } else {
            setUnitRoleList(
                    unitRoles.stream()
                            .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                            .map(RoleVO::from)
                            .toList());
        }
        rebuildEffectiveRoles();
    }

    public void rebuildEffectiveRoles() {
        Map<Long, RoleVO> map = new LinkedHashMap<>();
        if (roleList != null) {
            for (RoleVO r : roleList) {
                map.put(r.getId(), r);
            }
        }
        if (unitRoleList != null) {
            for (RoleVO r : unitRoleList) {
                map.putIfAbsent(r.getId(), r);
            }
        }
        setEffectiveRoleList(new ArrayList<>(map.values()));
    }
}
