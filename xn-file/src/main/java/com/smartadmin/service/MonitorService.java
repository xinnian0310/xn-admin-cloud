package com.smartadmin.service;

import com.smartadmin.dto.OnlineUserVO;
import com.smartadmin.dto.ServerMonitorVO;
import com.smartadmin.entity.Role;
import com.smartadmin.entity.User;
import com.smartadmin.repository.UserRepository;
import com.smartadmin.websocket.NoticeSessionHub;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MonitorService {

    private static final Logger log = LoggerFactory.getLogger(MonitorService.class);

    private final NoticeSessionHub sessionHub;
    private final UserRepository userRepository;
    private final RbacService rbacService;
    private final TokenBlacklistService tokenBlacklistService;

    public List<OnlineUserVO> onlineUsers() {
        rbacService.checkPermission("api:GET:/api/monitor/online");
        List<NoticeSessionHub.OnlineUserMeta> metas = sessionHub.getOnlineUsers();
        long now = System.currentTimeMillis();
        return metas.stream()
                .sorted(Comparator.comparingLong(NoticeSessionHub.OnlineUserMeta::connectedAt))
                .map(meta -> toVO(meta, now))
                .toList();
    }

    private OnlineUserVO toVO(NoticeSessionHub.OnlineUserMeta meta, long now) {
        OnlineUserVO vo = new OnlineUserVO();
        vo.setUserId(meta.userId());
        vo.setIp(meta.ip());
        vo.setSessionCount(meta.sessionCount());
        if (meta.connectedAt() > 0) {
            vo.setLoginTime(
                    LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(meta.connectedAt()), ZoneId.systemDefault()));
            vo.setOnlineSeconds(Math.max(0, (now - meta.connectedAt()) / 1000));
        }
        userRepository
                .findByIdWithRoles(meta.userId())
                .ifPresent(
                        user -> {
                            vo.setUsername(user.getUsername());
                            vo.setNickname(user.getNickname());
                            if (user.getUnit() != null) {
                                vo.setUnitName(user.getUnit().getName());
                            }
                            vo.setRoles(resolveRoleNames(user));
                        });
        return vo;
    }

    private String resolveRoleNames(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return "";
        }
        return user.getRoles().stream()
                .map(Role::getName)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("、"));
    }

    public int kick(Long userId) {
        rbacService.checkPermission("api:POST:/api/monitor/online/{userId}/kick");
        tokenBlacklistService.revokeUser(userId);
        return sessionHub.kickUser(userId);
    }

    public ServerMonitorVO server() {
        rbacService.checkPermission("api:GET:/api/monitor/server");
        ServerMonitorVO vo = new ServerMonitorVO();
        fillCpuAndMemory(vo);
        fillJvm(vo);
        fillSystem(vo);
        fillDisks(vo);
        return vo;
    }

    private void fillCpuAndMemory(ServerMonitorVO vo) {
        java.lang.management.OperatingSystemMXBean base =
                ManagementFactory.getOperatingSystemMXBean();
        vo.getCpu().setCores(base.getAvailableProcessors());
        vo.getSystem().setAvailableProcessors(base.getAvailableProcessors());
        if (base instanceof com.sun.management.OperatingSystemMXBean os) {
            vo.getCpu().setSysUsage(pct(os.getCpuLoad()));
            vo.getCpu().setProcessUsage(pct(os.getProcessCpuLoad()));

            long total = os.getTotalMemorySize();
            long free = os.getFreeMemorySize();
            long used = Math.max(0, total - free);
            vo.getMemory().setTotal(total);
            vo.getMemory().setFree(free);
            vo.getMemory().setUsed(used);
            vo.getMemory().setUsage(total > 0 ? round((double) used / total * 100) : 0);
        }
    }

    private void fillJvm(ServerMonitorVO vo) {
        Runtime rt = Runtime.getRuntime();
        long total = rt.totalMemory();
        long free = rt.freeMemory();
        long max = rt.maxMemory();
        long used = Math.max(0, total - free);
        ServerMonitorVO.Jvm jvm = vo.getJvm();
        jvm.setTotal(total);
        jvm.setFree(free);
        jvm.setUsed(used);
        jvm.setMax(max);
        long denom = max > 0 ? max : total;
        jvm.setUsage(denom > 0 ? round((double) used / denom * 100) : 0);
        jvm.setVersion(System.getProperty("java.version"));
        jvm.setVendor(System.getProperty("java.vendor"));
        jvm.setHome(System.getProperty("java.home"));

        RuntimeMXBean runtimeMX = ManagementFactory.getRuntimeMXBean();
        jvm.setStartTime(
                LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(runtimeMX.getStartTime()), ZoneId.systemDefault()));
        jvm.setUptimeSeconds(runtimeMX.getUptime() / 1000);
    }

    private void fillSystem(ServerMonitorVO vo) {
        ServerMonitorVO.SystemInfo sys = vo.getSystem();
        sys.setOsName(System.getProperty("os.name"));
        sys.setOsArch(System.getProperty("os.arch"));
        sys.setOsVersion(System.getProperty("os.version"));
        sys.setUserDir(System.getProperty("user.dir"));
        try {
            InetAddress local = InetAddress.getLocalHost();
            sys.setHostName(local.getHostName());
            sys.setIp(local.getHostAddress());
        } catch (Exception e) {
            log.debug("resolve local host failed", e);
            sys.setHostName("-");
            sys.setIp("-");
        }
    }

    private void fillDisks(ServerMonitorVO vo) {
        List<ServerMonitorVO.Disk> disks = new ArrayList<>();
        File[] roots = File.listRoots();
        if (roots == null) {
            return;
        }
        for (File root : roots) {
            long total = root.getTotalSpace();
            if (total <= 0) {
                continue;
            }
            long free = root.getUsableSpace();
            long used = Math.max(0, total - free);
            ServerMonitorVO.Disk disk = new ServerMonitorVO.Disk();
            disk.setName(root.getAbsolutePath());
            disk.setTotal(total);
            disk.setFree(free);
            disk.setUsed(used);
            disk.setUsage(round((double) used / total * 100));
            try {
                disk.setType(Files.getFileStore(root.toPath()).type());
            } catch (Exception e) {
                disk.setType("-");
            }
            disks.add(disk);
        }
        vo.setDisks(disks);
    }

    /** 0..1 的负载转百分比，负值(不可用)归零 */
    private double pct(double load) {
        if (load < 0 || Double.isNaN(load)) {
            return 0;
        }
        return round(load * 100);
    }

    private double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
