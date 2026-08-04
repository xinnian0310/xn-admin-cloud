package com.smartadmin.dto;

import lombok.Data;

import java.util.List;

@Data
public class SqlMonitorVO {

    private long queryCount;
    private int bufferSize;
    private List<SqlRecordVO> records;
}
