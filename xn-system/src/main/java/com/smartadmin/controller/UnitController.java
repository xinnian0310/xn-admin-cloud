package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.IdsRequest;
import com.smartadmin.dto.RoleIdsRequest;
import com.smartadmin.dto.UnitRequest;
import com.smartadmin.dto.UnitVO;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.UnitService;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/units")
@RequiredArgsConstructor
public class UnitController {

    private final UnitService unitService;

    @GetMapping("/tree")
    public ApiResponse<List<UnitVO>> tree(
            @RequestParam(value = "FuzzyWord", required = false) String fuzzyWord,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : fuzzyWord;
        return ApiResponse.success(unitService.tree(
                StringUtils.hasText(kw) ? kw.trim() : null,
                status
        ));
    }

    @GetMapping("/options")
    public ApiResponse<List<UnitVO>> options() {
        return ApiResponse.success(unitService.listOptions());
    }

    @GetMapping("/{id}")
    public ApiResponse<UnitVO> detail(@PathVariable Long id) {
        return ApiResponse.success(unitService.getById(id));
    }

    @PostMapping
    @OperLog(title = "单位管理", businessType = OperBusinessType.INSERT)
    public ApiResponse<UnitVO> create(@Valid @RequestBody UnitRequest request) {
        return ApiResponse.success("创建成功", unitService.create(request));
    }

    @PostMapping("/batch-delete")
    @OperLog(title = "单位管理", businessType = OperBusinessType.DELETE)
    public ApiResponse<Map<String, Integer>> batchDelete(@Valid @RequestBody IdsRequest request) {
        int count = unitService.batchDelete(request.getIds());
        return ApiResponse.success("删除成功", Map.of("count", count));
    }

    @PutMapping("/{id}")
    @OperLog(title = "单位管理", businessType = OperBusinessType.UPDATE)
    public ApiResponse<UnitVO> update(@PathVariable Long id, @Valid @RequestBody UnitRequest request) {
        return ApiResponse.success("更新成功", unitService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @OperLog(title = "单位管理", businessType = OperBusinessType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        unitService.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    @PutMapping("/{id}/status")
    @OperLog(title = "单位管理", businessType = OperBusinessType.UPDATE)
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null) {
            return ApiResponse.error(400, "状态不能为空");
        }
        unitService.updateStatus(id, status);
        return ApiResponse.success("状态更新成功", null);
    }

    @PutMapping("/{id}/roles")
    @OperLog(title = "单位管理", businessType = OperBusinessType.GRANT)
    public ApiResponse<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody RoleIdsRequest request) {
        unitService.assignRoles(id, request.getRoleIds());
        return ApiResponse.success("角色分配成功", null);
    }
}
