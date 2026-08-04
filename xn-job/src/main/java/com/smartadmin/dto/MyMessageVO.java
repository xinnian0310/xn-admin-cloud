package com.smartadmin.dto;

import com.smartadmin.entity.MessageStatus;
import com.smartadmin.entity.SysMessage;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class MyMessageVO {

    private Long id;
    private String title;
    private String content;
    private MessageStatus status;
    private LocalDateTime sentAt;
    private String senderName;
    private boolean read;
    private LocalDateTime readAt;
    private LocalDateTime receivedAt;

    public static MyMessageVO from(
            SysMessage message,
            boolean read,
            LocalDateTime readAt,
            LocalDateTime receivedAt,
            String senderName) {
        MyMessageVO vo = new MyMessageVO();
        vo.setId(message.getId());
        vo.setTitle(message.getTitle());
        vo.setContent(message.getContent());
        vo.setStatus(message.getStatus());
        vo.setSentAt(message.getSentAt());
        vo.setSenderName(senderName);
        vo.setRead(read);
        vo.setReadAt(readAt);
        vo.setReceivedAt(receivedAt);
        return vo;
    }
}
