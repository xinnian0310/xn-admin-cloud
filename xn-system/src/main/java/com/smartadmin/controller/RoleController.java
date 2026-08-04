package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.IdsRequest;
import com.smartadmin.dto.PageResult;
import com.smartadmin.dto.RoleDetailVO;
import com.smartadmin.dto.RoleRequest;
import com.smartadmin.dto.RoleVO;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.RoleService;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/options")
    public ApiResponse<List<RoleVO>> options() {
        return ApiResponse.success(roleService.listOptions());
    }

    @GetMapping
    public ApiResponse<PageResult<RoleVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(roleService.list(page, size, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<RoleDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.success(roleService.getById(id));
    }

    @PostMapping
    @OperLog(title = "角色管理", businessType = OperBusinessType.INSERT)
    public ApiResponse<RoleVO> create(@Valid @RequestBody RoleRequest request) {
        return ApiResponse.success("创建成功", roleService.create(request));
    }

    @PostMapping("/batch-delete")
    @OperLog(title = "角色管理", businessType = OperBusinessType.DELETE)
    public ApiResponse<Map<String, Integer>> batchDelete(@Valid @RequestBody IdsRequest request) {
        int count = roleService.batchDelete(request.getIds());
        return ApiResponse.success("删除成功", Map.of("count", count));
    }

    @PutMapping("/{id}")
    @OperLog(title = "角色管理", businessType = OperBusinessType.UPDATE)
    public ApiResponse<RoleVO> update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        return ApiResponse.success("更新成功", roleService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @OperLog(title = "角色管理", businessType = OperBusinessType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    @PutMapping("/{id}/status")
    @OperLog(title = "角色管理", businessType = OperBusinessType.UPDATE)
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null) {
            return ApiResponse.error(400, "状态不能为空");
        }
        roleService.updateStatus(id, status);
        return ApiResponse.success("状态更新成功", null);
    }

    @PutMapping("/{id}/permissions")
    @OperLog(title = "角色管理", businessType = OperBusinessType.GRANT)
    public ApiResponse<Void> assignPermissions(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        List<Long> permissionIds = body.get("permissionIds");
        if (permissionIds == null) {
            return ApiResponse.error(400, "permissionIds 不能为空");
        }
        roleService.assignPermissions(id, permissionIds);
        return ApiResponse.success("权限分配成功", null);
    }
}
