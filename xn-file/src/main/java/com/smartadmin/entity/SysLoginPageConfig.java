package com.smartadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@Entity
@Table(name = "sys_login_page_config")
public class SysLoginPageConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 配置名称 */
    @Column(nullable = false, length = 50)
    private String name;

    /** 背景图 URL（外链或本地 /uploads/...） */
    @Column(length = 500)
    private String backgroundUrl;

    /** 背景适应模式： COVER 覆盖铺满 / CONTAIN 完整适应 / STRETCH 拉伸填满 / CENTER 居中原图 */
    @Column(nullable = false, length = 20)
    private String backgroundFit = "COVER";

    /** 登录框水平位置（相对视口宽度百分比 0–100）；为空表示默认居中 */
    @Column(name = "boxx")
    private Double boxX;

    /** 登录框垂直位置（相对视口高度百分比 0–100）；为空表示默认居中 */
    @Column(name = "boxy")
    private Double boxY;

    /** 是否开启登录验证 */
    @Column(nullable = false)
    private Boolean captchaEnabled = false;

    /** 验证类型：IMAGE 图形验证码 / SLIDER 滑块验证 未开启验证时可为空 */
    @Column(length = 20)
    private String captchaType;

    /** 1=启用（全局仅允许一条为启用） 0=未启用 */
    @Column(nullable = false)
    private Integer status = 0;

    @Column(length = 200)
    private String remark;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp private LocalDateTime updatedAt;
}
