package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.IdsRequest;
import com.smartadmin.dto.RouteCodegenRequest;
import com.smartadmin.dto.RouteCodegenVO;
import com.smartadmin.dto.RouteRequest;
import com.smartadmin.dto.RouteVO;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.RouteCodegenService;
import com.smartadmin.service.RouteService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
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
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;
    private final RouteCodegenService routeCodegenService;

    @GetMapping("/tree")
    public ApiResponse<List<RouteVO>> tree(
            @RequestParam(value = "FuzzyWord", required = false) String fuzzyWord,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "builtIn", required = false) String builtIn) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : fuzzyWord;
        return ApiResponse.success(
                routeService.tree(
                        StringUtils.hasText(kw) ? kw.trim() : null,
                        StringUtils.hasText(type) ? type.trim() : null,
                        parseInteger(status),
                        parseBoolean(builtIn)));
    }

    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Boolean parseBoolean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        if ("true".equals(normalized) || "1".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized)) {
            return false;
        }
        return null;
    }

    @GetMapping("/{id}")
    public ApiResponse<RouteVO> detail(@PathVariable Long id) {
        return ApiResponse.success(routeService.getById(id));
    }

    @PostMapping
    @OperLog(title = "路由管理", businessType = OperBusinessType.INSERT)
    public ApiResponse<RouteVO> create(@Valid @RequestBody RouteRequest request) {
        return ApiResponse.success("创建成功", routeService.create(request));
    }

    @PostMapping("/batch-delete")
    @OperLog(title = "路由管理", businessType = OperBusinessType.DELETE)
    public ApiResponse<Map<String, Integer>> batchDelete(@Valid @RequestBody IdsRequest request) {
        int count = routeService.batchDelete(request.getIds());
        return ApiResponse.success("删除成功", Map.of("count", count));
    }

    @PutMapping("/{id}")
    @OperLog(title = "路由管理", businessType = OperBusinessType.UPDATE)
    public ApiResponse<RouteVO> update(
            @PathVariable Long id, @Valid @RequestBody RouteRequest request) {
        return ApiResponse.success("更新成功", routeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @OperLog(title = "路由管理", businessType = OperBusinessType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        routeService.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    /** 代码生成：落库按钮/接口权限（可选）并返回前端/后端骨架与 SQL */
    @PostMapping("/{id}/generate")
    @OperLog(title = "路由管理", businessType = OperBusinessType.OTHER)
    public ApiResponse<RouteCodegenVO> generate(
            @PathVariable Long id, @Valid @RequestBody RouteCodegenRequest request) {
        return ApiResponse.success("生成成功", routeCodegenService.generate(id, request));
    }
}
