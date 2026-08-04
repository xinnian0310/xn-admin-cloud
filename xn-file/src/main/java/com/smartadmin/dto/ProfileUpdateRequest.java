package com.smartadmin.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileUpdateRequest {

    @Size(max = 50, message = "昵称长度不能超过50")
    private String nickname;

    @Size(max = 100, message = "邮箱长度不能超过100")
    private String email;

    @Size(max = 20, message = "手机号长度不能超过20")
    private String phone;

    /** 可选：修改密码，不填则不改（建议改用 /auth/me/password） */
    @Size(max = 50, message = "密码长度不能超过50")
    private String password;

    /** 头像 URL（上传接口返回后提交） */
    @Size(max = 500, message = "头像地址过长")
    private String avatar;
}
