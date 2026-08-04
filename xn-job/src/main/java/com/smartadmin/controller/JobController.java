package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.IdsRequest;
import com.smartadmin.dto.JobRequest;
import com.smartadmin.dto.JobVO;
import com.smartadmin.dto.PageResult;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.JobService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @GetMapping
    public ApiResponse<PageResult<JobVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success(jobService.list(page, size, keyword, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<JobVO> detail(@PathVariable Long id) {
        return ApiResponse.success(jobService.getById(id));
    }

    @PostMapping
    @OperLog(title = "定时任务", businessType = OperBusinessType.INSERT)
    public ApiResponse<JobVO> create(@Valid @RequestBody JobRequest request) {
        return ApiResponse.success(jobService.create(request));
    }

    @PutMapping("/{id}")
    @OperLog(title = "定时任务", businessType = OperBusinessType.UPDATE)
    public ApiResponse<JobVO> update(
            @PathVariable Long id, @Valid @RequestBody JobRequest request) {
        return ApiResponse.success(jobService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @OperLog(title = "定时任务", businessType = OperBusinessType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        jobService.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/batch-delete")
    @OperLog(title = "定时任务", businessType = OperBusinessType.DELETE)
    public ApiResponse<Map<String, Integer>> batchDelete(@Valid @RequestBody IdsRequest request) {
        int count = jobService.batchDelete(request.getIds());
        return ApiResponse.success("删除成功", Map.of("count", count));
    }

    @PutMapping("/{id}/status")
    @OperLog(title = "定时任务", businessType = OperBusinessType.UPDATE)
    public ApiResponse<JobVO> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        return ApiResponse.success(jobService.changeStatus(id, status));
    }

    @PostMapping("/{id}/run")
    @OperLog(title = "定时任务", businessType = OperBusinessType.OTHER)
    public ApiResponse<JobVO> runOnce(@PathVariable Long id) {
        return ApiResponse.success(jobService.runOnce(id));
    }
}
