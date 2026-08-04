package com.smartadmin.dto;

import java.util.List;
import lombok.Data;

@Data
public class SqlMonitorVO {

    private long queryCount;
    private int bufferSize;
    private List<SqlRecordVO> records;
}
