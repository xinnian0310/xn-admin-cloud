package com.smartadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UserRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过50")
    private String username;

    /** 长度与复杂度由密码策略校验 */
    @Size(max = 50, message = "密码长度不能超过50")
    private String password;

    @Size(max = 50, message = "昵称长度不能超过50")
    private String nickname;

    @Size(max = 100, message = "邮箱长度不能超过100")
    private String email;

    @Size(max = 20, message = "手机号长度不能超过20")
    private String phone;

    private Integer status = 1;

    /** 可空：若所属单位已绑定默认角色，可不选个人角色 */
    private List<Long> roleIds;

    /** 所属单位，可空 */
    private Long unitId;

    /** 岗位，可空 */
    private Long postId;

    /** @deprecated 兼容旧客户端，优先使用 roleIds */
    private String role;
}
