package com.smartadmin.dto;

import com.smartadmin.entity.SysRecycleBin;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RecycleBinVO {

    private Long id;
    private String bizType;
    private Long bizId;
    private String title;
    private String summary;
    private String deletedBy;
    private LocalDateTime deletedAt;

    public static RecycleBinVO from(SysRecycleBin entity) {
        RecycleBinVO vo = new RecycleBinVO();
        vo.setId(entity.getId());
        vo.setBizType(entity.getBizType());
        vo.setBizId(entity.getBizId());
        vo.setTitle(entity.getTitle());
        vo.setSummary(entity.getSummary());
        vo.setDeletedBy(entity.getDeletedBy());
        vo.setDeletedAt(entity.getDeletedAt());
        return vo;
    }
}
