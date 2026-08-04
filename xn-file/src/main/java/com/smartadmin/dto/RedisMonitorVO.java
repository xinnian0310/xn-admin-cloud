package com.smartadmin.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RedisMonitorVO {

    /** ENABLED | DISABLED | ERROR */
    private String status;
    private String message;
    private String host;
    private Integer port;
    private Long keyCount;
    private Map<String, String> info;
    private List<String> sampleKeys;
}
