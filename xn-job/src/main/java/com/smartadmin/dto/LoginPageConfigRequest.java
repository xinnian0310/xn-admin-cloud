package com.smartadmin.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginPageConfigRequest {

    @NotBlank(message = "配置名称不能为空")
    @Size(max = 50, message = "配置名称长度不能超过50")
    private String name;

    @Size(max = 500, message = "背景图地址长度不能超过500")
    private String backgroundUrl;

    /** COVER 覆盖铺满 / CONTAIN 完整适应 / STRETCH 拉伸填满 / CENTER 居中原图 */
    @Size(max = 20, message = "背景适应模式长度不能超过20")
    private String backgroundFit = "COVER";

    /** 为空表示默认居中 */
    @DecimalMin(value = "0", message = "水平位置需在 0–100 之间")
    @DecimalMax(value = "100", message = "水平位置需在 0–100 之间")
    private Double boxX;

    /** 为空表示默认居中 */
    @DecimalMin(value = "0", message = "垂直位置需在 0–100 之间")
    @DecimalMax(value = "100", message = "垂直位置需在 0–100 之间")
    private Double boxY;

    @NotNull(message = "是否开启验证不能为空")
    private Boolean captchaEnabled = false;

    /** IMAGE / SLIDER，开启验证时必填 */
    @Size(max = 20, message = "验证类型长度不能超过20")
    private String captchaType;

    /** 1=启用 0=未启用；启用时会自动关闭其它配置 */
    private Integer status = 0;

    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;
}
