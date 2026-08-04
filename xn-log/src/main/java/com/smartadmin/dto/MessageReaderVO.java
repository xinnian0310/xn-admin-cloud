package com.smartadmin.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class MessageReaderVO {

    private Long userId;
    private String username;
    private String nickname;
    private LocalDateTime readAt;
}
