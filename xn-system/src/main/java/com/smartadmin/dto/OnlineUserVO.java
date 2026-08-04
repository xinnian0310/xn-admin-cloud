package com.smartadmin.dto;

import java.time.LocalDateTime;
import lombok.Data;

/** 在线用户 */
@Data
public class OnlineUserVO {
    private Long userId;
    private String username;
    private String nickname;
    private String unitName;
    private String roles;
    private String ip;

    /** 在线连接数（同一用户可多端） */
    private int sessionCount;

    /** 登录（建立连接）时间 */
    private LocalDateTime loginTime;

    /** 已在线秒数 */
    private long onlineSeconds;
}
