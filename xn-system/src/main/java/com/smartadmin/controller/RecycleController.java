package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.IdsRequest;
import com.smartadmin.dto.PageResult;
import com.smartadmin.dto.RecycleBinVO;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.RecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/recycle")
@RequiredArgsConstructor
public class RecycleController {

    private final RecycleService recycleService;

    @GetMapping
    public ApiResponse<PageResult<RecycleBinVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String bizType) {
        return ApiResponse.success(recycleService.list(page, size, keyword, bizType));
    }

    @PostMapping("/{id}/restore")
    @OperLog(title = "回收站", businessType = OperBusinessType.OTHER)
    public ApiResponse<Void> restore(@PathVariable Long id) {
        recycleService.restore(id);
        return ApiResponse.success("已恢复", null);
    }

    @DeleteMapping("/{id}")
    @OperLog(title = "回收站", businessType = OperBusinessType.DELETE)
    public ApiResponse<Void> purge(@PathVariable Long id) {
        recycleService.purge(id);
        return ApiResponse.success("已彻底删除", null);
    }

    @PostMapping("/batch-delete")
    @OperLog(title = "回收站", businessType = OperBusinessType.DELETE)
    public ApiResponse<Map<String, Integer>> batchPurge(@Valid @RequestBody IdsRequest request) {
        int count = recycleService.batchPurge(request.getIds());
        return ApiResponse.success("已彻底删除", Map.of("count", count));
    }

    @DeleteMapping("/clean")
    @OperLog(title = "回收站", businessType = OperBusinessType.CLEAN)
    public ApiResponse<Void> clean() {
        recycleService.clean();
        return ApiResponse.success("回收站已清空", null);
    }
}
