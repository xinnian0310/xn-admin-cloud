package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.MenuPermissionGroupVO;
import com.smartadmin.dto.PermissionRequest;
import com.smartadmin.dto.PermissionVO;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping("/tree")
    public ApiResponse<List<PermissionVO>> tree() {
        return ApiResponse.success(permissionService.tree());
    }

    @GetMapping("/{id}/groups")
    public ApiResponse<MenuPermissionGroupVO> groupedByMenu(@PathVariable Long id) {
        return ApiResponse.success(permissionService.groupedByMenu(id));
    }

    @PostMapping
    @OperLog(title = "权限内容", businessType = OperBusinessType.INSERT)
    public ApiResponse<PermissionVO> create(@Valid @RequestBody PermissionRequest request) {
        return ApiResponse.success("创建成功", permissionService.create(request));
    }

    @PutMapping("/{id}")
    @OperLog(title = "权限内容", businessType = OperBusinessType.UPDATE)
    public ApiResponse<PermissionVO> update(@PathVariable Long id, @Valid @RequestBody PermissionRequest request) {
        return ApiResponse.success("更新成功", permissionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @OperLog(title = "权限内容", businessType = OperBusinessType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
