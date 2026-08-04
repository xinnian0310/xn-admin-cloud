package com.smartadmin.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageReaderVO {

    private Long userId;
    private String username;
    private String nickname;
    private LocalDateTime readAt;
}
