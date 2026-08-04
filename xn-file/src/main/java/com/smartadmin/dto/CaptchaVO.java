package com.smartadmin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaVO {

    private String captchaId;
    /** IMAGE / SLIDER */
    private String captchaType;
    /** IMAGE 时返回 PNG data URL；SLIDER 为空 */
    private String imageBase64;
}
