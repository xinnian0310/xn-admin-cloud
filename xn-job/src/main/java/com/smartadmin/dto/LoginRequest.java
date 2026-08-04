package com.smartadmin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 验证码 ID（登录页开启验证码时必填） */
    private String captchaId;

    /** 图形验证码用户输入；滑块验证通过后可为空 */
    private String captchaCode;
}
