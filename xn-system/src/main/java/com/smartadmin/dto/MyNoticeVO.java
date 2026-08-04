package com.smartadmin.dto;

import com.smartadmin.entity.SysNotice;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MyNoticeVO {

    private Long id;
    private String title;
    private String content;
    private LocalDateTime publishedAt;
    /** 接收时间（下发时写入接收记录的时间） */
    private LocalDateTime receivedAt;
    private boolean read;
    private LocalDateTime readAt;
    private Long publisherId;
    private String publisherName;

    public static MyNoticeVO from(SysNotice notice,
                                  boolean read,
                                  LocalDateTime readAt,
                                  LocalDateTime receivedAt,
                                  String publisherName) {
        MyNoticeVO vo = new MyNoticeVO();
        vo.setId(notice.getId());
        vo.setTitle(notice.getTitle());
        vo.setContent(notice.getContent());
        vo.setPublishedAt(notice.getPublishedAt());
        vo.setReceivedAt(receivedAt);
        vo.setRead(read);
        vo.setReadAt(readAt);
        vo.setPublisherId(notice.getPublisherId());
        vo.setPublisherName(publisherName);
        return vo;
    }
}
