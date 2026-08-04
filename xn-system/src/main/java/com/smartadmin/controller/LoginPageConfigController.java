package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.IdsRequest;
import com.smartadmin.dto.LoginPageConfigRequest;
import com.smartadmin.dto.LoginPageConfigVO;
import com.smartadmin.dto.PageResult;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.LoginPageConfigService;
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
@RequestMapping("/api/login-page-configs")
@RequiredArgsConstructor
public class LoginPageConfigController {

    private final LoginPageConfigService loginPageConfigService;

    /** 当前启用的登录页配置（无需鉴权） */
    @GetMapping("/active")
    public ApiResponse<LoginPageConfigVO> active() {
        return ApiResponse.success(loginPageConfigService.getActive());
    }

    @GetMapping
    public ApiResponse<PageResult<LoginPageConfigVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success(loginPageConfigService.list(page, size, keyword, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<LoginPageConfigVO> detail(@PathVariable Long id) {
        return ApiResponse.success(loginPageConfigService.getById(id));
    }

    @PostMapping
    @OperLog(title = "登录页设置", businessType = OperBusinessType.INSERT)
    public ApiResponse<LoginPageConfigVO> create(
            @Valid @RequestBody LoginPageConfigRequest request) {
        return ApiResponse.success("创建成功", loginPageConfigService.create(request));
    }

    @PutMapping("/{id}")
    @OperLog(title = "登录页设置", businessType = OperBusinessType.UPDATE)
    public ApiResponse<LoginPageConfigVO> update(
            @PathVariable Long id, @Valid @RequestBody LoginPageConfigRequest request) {
        return ApiResponse.success("更新成功", loginPageConfigService.update(id, request));
    }

    @PutMapping("/{id}/status")
    @OperLog(title = "登录页设置", businessType = OperBusinessType.UPDATE)
    public ApiResponse<Void> updateStatus(
            @PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null) {
            return ApiResponse.error(400, "状态不能为空");
        }
        loginPageConfigService.updateStatus(id, status);
        return ApiResponse.success(status == 1 ? "已启用" : "已停用", null);
    }

    @DeleteMapping("/{id}")
    @OperLog(title = "登录页设置", businessType = OperBusinessType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        loginPageConfigService.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/batch-delete")
    @OperLog(title = "登录页设置", businessType = OperBusinessType.DELETE)
    public ApiResponse<Map<String, Integer>> batchDelete(@Valid @RequestBody IdsRequest request) {
        int count = loginPageConfigService.batchDelete(request.getIds());
        return ApiResponse.success("删除成功", Map.of("count", count));
    }
}
