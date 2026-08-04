package com.smartadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SliderVerifyRequest {

    @NotBlank(message = "验证码 ID 不能为空")
    private String captchaId;

    @NotNull(message = "滑块进度不能为空")
    private Integer percent;
}
