package com.smartadmin.dto;

import com.smartadmin.entity.SysLoginLog;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class LoginLogVO {

    private Long id;
    private String username;
    private String ip;
    private String userAgent;
    private Integer status;
    private String message;
    private LocalDateTime loginTime;

    public static LoginLogVO from(SysLoginLog entity) {
        LoginLogVO vo = new LoginLogVO();
        vo.setId(entity.getId());
        vo.setUsername(entity.getUsername());
        vo.setIp(entity.getIp());
        vo.setUserAgent(entity.getUserAgent());
        vo.setStatus(entity.getStatus());
        vo.setMessage(entity.getMessage());
        vo.setLoginTime(entity.getLoginTime());
        return vo;
    }
}
