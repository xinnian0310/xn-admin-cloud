package com.smartadmin.service;

import com.smartadmin.dto.DashboardStatsVO;
import com.smartadmin.entity.NoticeStatus;
import com.smartadmin.entity.SysNotice;
import com.smartadmin.entity.User;
import com.smartadmin.repository.RoleRepository;
import com.smartadmin.repository.SysNoticeRepository;
import com.smartadmin.repository.SysUnitRepository;
import com.smartadmin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int TREND_DAYS = 30;
    private static final int MAX_UNIT_SLICES = 8;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SysUnitRepository unitRepository;
    private final SysNoticeRepository noticeRepository;
    private final RbacService rbacService;

    public DashboardStatsVO getStats() {
        rbacService.checkPermission("api:GET:/api/dashboard/stats");

        DashboardStatsVO vo = new DashboardStatsVO();

        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime yesterdayStart = today.minusDays(1).atStartOfDay();

        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus(1);

        // KPI
        vo.setTotalUsers(totalUsers);
        vo.setActiveUsers(activeUsers);
        vo.setAdminUsers(userRepository.countByRole("ADMIN"));
        vo.setTodayNewUsers(userRepository.countByCreatedAtAfter(todayStart));
        vo.setYesterdayNewUsers(userRepository.countByCreatedAtBetween(yesterdayStart, todayStart));
        vo.setTotalRoles(roleRepository.count());
        vo.setTotalUnits(unitRepository.count());
        vo.setPublishedNotices(noticeRepository.countByStatus(NoticeStatus.PUBLISHED));

        // 分布
        vo.setRoleDistribution(toNameValues(userRepository.countGroupByRole(), Integer.MAX_VALUE));
        vo.setUnitDistribution(toNameValues(userRepository.countGroupByUnit(), MAX_UNIT_SLICES));
        vo.setStatusDistribution(List.of(
                new DashboardStatsVO.NameValue("启用", activeUsers),
                new DashboardStatsVO.NameValue("禁用", Math.max(0, totalUsers - activeUsers))
        ));

        // 近 30 天注册趋势
        vo.setRegisterTrend(buildRegisterTrend(today));

        // 最新公告
        vo.setRecentNotices(buildRecentNotices());

        return vo;
    }

    private List<DashboardStatsVO.NameValue> toNameValues(List<Object[]> rows, int limit) {
        List<DashboardStatsVO.NameValue> list = new ArrayList<>();
        for (Object[] row : rows) {
            if (list.size() >= limit) {
                break;
            }
            String name = row[0] != null ? row[0].toString() : "未命名";
            long value = row[1] != null ? ((Number) row[1]).longValue() : 0;
            list.add(new DashboardStatsVO.NameValue(name, value));
        }
        return list;
    }

    private List<DashboardStatsVO.TrendPoint> buildRegisterTrend(LocalDate today) {
        LocalDate startDate = today.minusDays(TREND_DAYS - 1L);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (int i = 0; i < TREND_DAYS; i++) {
            counts.put(startDate.plusDays(i).format(DAY), 0L);
        }
        List<LocalDateTime> createdAts = userRepository.findCreatedAtAfter(startDate.atStartOfDay());
        for (LocalDateTime createdAt : createdAts) {
            if (createdAt == null) {
                continue;
            }
            String key = createdAt.toLocalDate().format(DAY);
            counts.computeIfPresent(key, (k, v) -> v + 1);
        }
        return counts.entrySet().stream()
                .map(e -> new DashboardStatsVO.TrendPoint(e.getKey(), e.getValue()))
                .toList();
    }

    private List<DashboardStatsVO.RecentNotice> buildRecentNotices() {
        List<SysNotice> notices = noticeRepository.findTop5ByStatusOrderByPublishedAtDesc(NoticeStatus.PUBLISHED);
        if (notices.isEmpty()) {
            return List.of();
        }
        Map<Long, String> publisherNames = loadPublisherNames(notices);
        return notices.stream().map(n -> {
            DashboardStatsVO.RecentNotice item = new DashboardStatsVO.RecentNotice();
            item.setId(n.getId());
            item.setTitle(n.getTitle());
            item.setStatus(n.getStatus() != null ? n.getStatus().name() : null);
            item.setPublishedAt(n.getPublishedAt());
            item.setPublisherName(publisherNames.get(n.getPublisherId()));
            return item;
        }).toList();
    }

    private Map<Long, String> loadPublisherNames(List<SysNotice> notices) {
        List<Long> ids = notices.stream()
                .map(SysNotice::getPublisherId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername()
                ));
    }
}
