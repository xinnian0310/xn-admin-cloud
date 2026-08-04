package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.dto.DashboardStatsVO;
import com.smartadmin.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public ApiResponse<DashboardStatsVO> stats() {
        return ApiResponse.success(dashboardService.getStats());
    }
}
