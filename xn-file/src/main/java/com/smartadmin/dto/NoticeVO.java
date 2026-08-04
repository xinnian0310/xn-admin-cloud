package com.smartadmin.dto;

import com.smartadmin.entity.NoticeStatus;
import com.smartadmin.entity.SysNotice;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoticeVO {

    private Long id;
    private String title;
    private String content;
    private NoticeStatus status;
    private Long publisherId;
    private String publisherName;
    private LocalDateTime publishedAt;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long readCount;
    private long totalCount;

    public static NoticeVO from(SysNotice notice) {
        NoticeVO vo = new NoticeVO();
        vo.setId(notice.getId());
        vo.setTitle(notice.getTitle());
        vo.setContent(notice.getContent());
        vo.setStatus(notice.getStatus());
        vo.setPublisherId(notice.getPublisherId());
        vo.setPublishedAt(notice.getPublishedAt());
        vo.setRevokedAt(notice.getRevokedAt());
        vo.setCreatedAt(notice.getCreatedAt());
        vo.setUpdatedAt(notice.getUpdatedAt());
        return vo;
    }
}
