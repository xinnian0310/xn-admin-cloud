package com.smartadmin.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 服务监控：CPU / 内存 / JVM / 服务器信息 / 磁盘 */
@Data
public class ServerMonitorVO {

    private Cpu cpu = new Cpu();
    private Memory memory = new Memory();
    private Jvm jvm = new Jvm();
    private SystemInfo system = new SystemInfo();
    private List<Disk> disks = new ArrayList<>();

    @Data
    public static class Cpu {
        /** 逻辑核心数 */
        private int cores;

        /** 系统整体 CPU 使用率(%) */
        private double sysUsage;

        /** 当前进程 CPU 使用率(%) */
        private double processUsage;
    }

    /** 物理内存，单位字节 */
    @Data
    public static class Memory {
        private long total;
        private long used;
        private long free;
        private double usage;
    }

    /** JVM 内存与运行时信息，内存单位字节 */
    @Data
    public static class Jvm {
        private long total;
        private long used;
        private long free;
        private long max;
        private double usage;
        private String version;
        private String vendor;
        private String home;
        private LocalDateTime startTime;

        /** 运行时长（秒） */
        private long uptimeSeconds;
    }

    @Data
    public static class SystemInfo {
        private String osName;
        private String osArch;
        private String osVersion;
        private String hostName;
        private String ip;
        private String userDir;
        private int availableProcessors;
    }

    /** 磁盘分区，单位字节 */
    @Data
    public static class Disk {
        private String name;
        private String type;
        private long total;
        private long used;
        private long free;
        private double usage;
    }
}
