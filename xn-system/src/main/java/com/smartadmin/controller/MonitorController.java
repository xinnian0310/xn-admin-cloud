package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.InfraStatusVO;
import com.smartadmin.dto.OnlineUserVO;
import com.smartadmin.dto.RedisMonitorVO;
import com.smartadmin.dto.ServerMonitorVO;
import com.smartadmin.dto.SqlMonitorVO;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.InfraRestartService;
import com.smartadmin.service.InfraStatusService;
import com.smartadmin.service.MonitorService;
import com.smartadmin.service.RedisMonitorService;
import com.smartadmin.service.SqlMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;
    private final RedisMonitorService redisMonitorService;
    private final SqlMonitorService sqlMonitorService;
    private final InfraStatusService infraStatusService;
    private final InfraRestartService infraRestartService;

    @GetMapping("/infra")
    public ApiResponse<InfraStatusVO> infra() {
        return ApiResponse.success(infraStatusService.status());
    }

    @PostMapping("/infra/{name}/restart")
    @OperLog(title = "基础设施重启", businessType = OperBusinessType.UPDATE)
    public ApiResponse<Map<String, Object>> restartInfra(@PathVariable String name) {
        return ApiResponse.success(infraRestartService.restart(name));
    }

    @GetMapping("/online")
    public ApiResponse<List<OnlineUserVO>> online() {
        return ApiResponse.success(monitorService.onlineUsers());
    }

    @PostMapping("/online/{userId}/kick")
    public ApiResponse<Map<String, Integer>> kick(@PathVariable Long userId) {
        int count = monitorService.kick(userId);
        return ApiResponse.success("操作成功", Map.of("count", count));
    }

    @GetMapping("/server")
    public ApiResponse<ServerMonitorVO> server() {
        return ApiResponse.success(monitorService.server());
    }

    @GetMapping("/redis")
    public ApiResponse<RedisMonitorVO> redis() {
        return ApiResponse.success(redisMonitorService.info());
    }

    @DeleteMapping("/redis/keys")
    @OperLog(title = "Redis监控", businessType = OperBusinessType.DELETE)
    public ApiResponse<Void> deleteRedisKey(@RequestParam String key) {
        redisMonitorService.deleteKey(key);
        return ApiResponse.success("删除成功", null);
    }

    @DeleteMapping("/redis/flush")
    @OperLog(title = "Redis监控", businessType = OperBusinessType.CLEAN)
    public ApiResponse<Void> flushRedis() {
        redisMonitorService.flushDb();
        return ApiResponse.success("清空成功", null);
    }

    @GetMapping("/sql")
    public ApiResponse<SqlMonitorVO> sql() {
        return ApiResponse.success(sqlMonitorService.snapshot());
    }

    @DeleteMapping("/sql/clean")
    @OperLog(title = "SQL监控", businessType = OperBusinessType.CLEAN)
    public ApiResponse<Void> cleanSql() {
        sqlMonitorService.clean();
        return ApiResponse.success("清空成功", null);
    }

    @DeleteMapping("/sql/records/{id}")
    @OperLog(title = "SQL监控", businessType = OperBusinessType.DELETE)
    public ApiResponse<Void> removeSqlRecord(@PathVariable Long id) {
        sqlMonitorService.removeRecord(id);
        return ApiResponse.success("删除成功", null);
    }
}
