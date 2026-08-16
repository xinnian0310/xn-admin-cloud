package com.smartadmin.entity;

import com.smartadmin.dto.AttachmentItem;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@Entity
@Table(name = "sys_message")
public class SysMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    /** 全部附件；空表示无附件 */
    @Convert(converter = AttachmentListConverter.class)
    @Column(name = "attachments", columnDefinition = "LONGTEXT")
    private List<AttachmentItem> attachments;

    @Column(name = "sender_id")
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageStatus status = MessageStatus.DRAFT;

    private LocalDateTime sentAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp private LocalDateTime updatedAt;
}
