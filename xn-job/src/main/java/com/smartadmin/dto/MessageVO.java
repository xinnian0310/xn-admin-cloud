package com.smartadmin.dto;

import com.smartadmin.entity.MessageStatus;
import com.smartadmin.entity.SysMessage;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class MessageVO {

    private Long id;
    private String title;
    private String content;
    private List<AttachmentItem> attachments;

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
        vo.setAttachments(AttachmentSupport.resolve(message.getAttachments()));
        vo.setSenderId(message.getSenderId());
        vo.setStatus(message.getStatus());
        vo.setSentAt(message.getSentAt());
        vo.setCreatedAt(message.getCreatedAt());
        vo.setUpdatedAt(message.getUpdatedAt());
        return vo;
    }
}
