package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.UserUiConfigVO;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.UserUiConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user-ui-config")
@RequiredArgsConstructor
public class UserUiConfigController {

    private final UserUiConfigService userUiConfigService;

    @GetMapping
    public ApiResponse<UserUiConfigVO> get() {
        return ApiResponse.success(userUiConfigService.getForCurrentUser());
    }

    @PutMapping
    @OperLog(title = "个人布局配置", businessType = OperBusinessType.UPDATE)
    public ApiResponse<UserUiConfigVO> save(@Valid @RequestBody UserUiConfigVO request) {
        return ApiResponse.success("保存成功", userUiConfigService.saveForCurrentUser(request));
    }

    @DeleteMapping
    @OperLog(title = "个人布局配置", businessType = OperBusinessType.DELETE)
    public ApiResponse<Void> reset() {
        userUiConfigService.resetForCurrentUser();
        return ApiResponse.success("已恢复为通用配置", null);
    }
}
