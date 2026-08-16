package com.smartadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 50, message = "用户名长度需在2-50之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 昵称，可选；为空时默认使用用户名 */
    @Size(max = 50, message = "昵称长度不能超过50")
    private String nickname;

    /** 验证码 ID（登录页开启验证码时必填） */
    private String captchaId;

    /** 图形验证码用户输入；滑块验证通过后可为空 */
    private String captchaCode;
}
