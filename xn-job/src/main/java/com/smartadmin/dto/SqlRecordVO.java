package com.smartadmin.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SqlRecordVO {

    private Long id;
    private String sql;
    private Long durationMs;
    private LocalDateTime executedAt;
}
