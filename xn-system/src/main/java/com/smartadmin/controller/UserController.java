package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.IdsRequest;
import com.smartadmin.dto.ImportResultVO;
import com.smartadmin.dto.PageResult;
import com.smartadmin.dto.UserImportRow;
import com.smartadmin.dto.UserRequest;
import com.smartadmin.dto.UserVO;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.UserService;
import com.smartadmin.util.ExcelHttpResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<PageResult<UserVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Long unitId) {
        return ApiResponse.success(userService.list(page, size, keyword, roleId, unitId));
    }

    @GetMapping("/export")
    @OperLog(title = "用户管理", businessType = OperBusinessType.EXPORT)
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) Long unitId) {
        return ExcelHttpResponse.xlsx(
                userService.exportExcel(keyword, roleId, unitId), "users.xlsx");
    }

    @GetMapping("/{id}")
    public ApiResponse<UserVO> detail(@PathVariable Long id) {
        return ApiResponse.success(userService.getById(id));
    }

    @PostMapping
    @OperLog(title = "用户管理", businessType = OperBusinessType.INSERT)
    public ApiResponse<UserVO> create(@Valid @RequestBody UserRequest request) {
        return ApiResponse.success("创建成功", userService.create(request));
    }

    @PostMapping("/batch-delete")
    @OperLog(title = "用户管理", businessType = OperBusinessType.DELETE)
    public ApiResponse<Map<String, Integer>> batchDelete(@Valid @RequestBody IdsRequest request) {
        int count = userService.batchDelete(request.getIds());
        return ApiResponse.success("删除成功", Map.of("count", count));
    }

    @PostMapping("/import")
    @OperLog(title = "用户管理", businessType = OperBusinessType.IMPORT)
    public ApiResponse<ImportResultVO> importUsers(@RequestBody List<UserImportRow> rows) {
        return ApiResponse.success("导入完成", userService.importUsers(rows));
    }

    @PutMapping("/{id}")
    @OperLog(title = "用户管理", businessType = OperBusinessType.UPDATE)
    public ApiResponse<UserVO> update(
            @PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return ApiResponse.success("更新成功", userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @OperLog(title = "用户管理", businessType = OperBusinessType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    @PatchMapping("/{id}/status")
    @OperLog(title = "用户管理", businessType = OperBusinessType.UPDATE)
    public ApiResponse<Void> updateStatus(
            @PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null) {
            return ApiResponse.error(400, "状态不能为空");
        }
        userService.updateStatus(id, status);
        return ApiResponse.success("状态更新成功", null);
    }
}
