package com.smartadmin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作台聚合数据。
 * 单一 /dashboard/stats 接口返回，避免新增受权限管控的额外端点。
 */
@Data
public class DashboardStatsVO {

    // ===== 核心指标（KPI） =====
    private long totalUsers;
    private long activeUsers;
    private long adminUsers;
    private long todayNewUsers;
    /** 昨日新增，用于「较昨日」环比 */
    private long yesterdayNewUsers;
    private long totalRoles;
    private long totalUnits;
    /** 已下发公告数 */
    private long publishedNotices;

    // ===== 分布（饼图 / 柱状图） =====
    /** 按角色的人数分布 */
    private List<NameValue> roleDistribution = new ArrayList<>();
    /** 按单位的人数分布 */
    private List<NameValue> unitDistribution = new ArrayList<>();
    /** 启用 / 禁用分布 */
    private List<NameValue> statusDistribution = new ArrayList<>();

    // ===== 近 30 天注册趋势（折线图） =====
    private List<TrendPoint> registerTrend = new ArrayList<>();

    // ===== 最新公告列表 =====
    private List<RecentNotice> recentNotices = new ArrayList<>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class NameValue {
        private String name;
        private long value;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TrendPoint {
        /** yyyy-MM-dd */
        private String date;
        private long count;
    }

    @Data
    @NoArgsConstructor
    public static class RecentNotice {
        private Long id;
        private String title;
        private String status;
        private LocalDateTime publishedAt;
        private String publisherName;
    }
}
