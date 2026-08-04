package com.smartadmin.dto;

import com.smartadmin.entity.MessageStatus;
import com.smartadmin.entity.SysMessage;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageVO {

    private Long id;
    private String title;
    private String content;
    private Long senderId;
    private String senderName;
    private MessageStatus status;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long totalCount;
    private Long readCount;

    public static MessageVO from(SysMessage message) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setTitle(message.getTitle());
        vo.setContent(message.getContent());
        vo.setSenderId(message.getSenderId());
        vo.setStatus(message.getStatus());
        vo.setSentAt(message.getSentAt());
        vo.setCreatedAt(message.getCreatedAt());
        vo.setUpdatedAt(message.getUpdatedAt());
        return vo;
    }
}
