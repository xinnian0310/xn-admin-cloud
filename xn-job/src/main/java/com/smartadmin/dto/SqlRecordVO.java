package com.smartadmin.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SqlRecordVO {

    private Long id;
    private String sql;
    private Long durationMs;
    private LocalDateTime executedAt;
}
