package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.ExceptionLogVO;
import com.smartadmin.dto.IdsRequest;
import com.smartadmin.dto.PageResult;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.ExceptionLogService;
import com.smartadmin.util.ExcelHttpResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/logs/exception")
@RequiredArgsConstructor
public class ExceptionLogController {

    private final ExceptionLogService exceptionLogService;

    @GetMapping
    public ApiResponse<PageResult<ExceptionLogVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ApiResponse.success(exceptionLogService.list(page, size, keyword, beginTime, endTime));
    }

    @GetMapping("/export")
    @OperLog(title = "异常日志", businessType = OperBusinessType.EXPORT)
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ExcelHttpResponse.xlsx(
                exceptionLogService.exportExcel(keyword, beginTime, endTime),
                "exception-logs.xlsx");
    }

    @GetMapping("/{id}")
    public ApiResponse<ExceptionLogVO> detail(@PathVariable Long id) {
        return ApiResponse.success(exceptionLogService.getById(id));
    }

    @DeleteMapping("/{id}")
    @OperLog(title = "异常日志", businessType = OperBusinessType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        exceptionLogService.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/batch-delete")
    @OperLog(title = "异常日志", businessType = OperBusinessType.DELETE)
    public ApiResponse<Map<String, Integer>> batchDelete(@Valid @RequestBody IdsRequest request) {
        int count = exceptionLogService.batchDelete(request.getIds());
        return ApiResponse.success("删除成功", Map.of("count", count));
    }

    @DeleteMapping("/clean")
    @OperLog(title = "异常日志", businessType = OperBusinessType.CLEAN)
    public ApiResponse<Void> clean() {
        exceptionLogService.clean();
        return ApiResponse.success("清空成功", null);
    }
}
