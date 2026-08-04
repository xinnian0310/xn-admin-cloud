package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.AppConfigVO;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.AppConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/system-config")
@RequiredArgsConstructor
public class AppConfigController {

    private final AppConfigService appConfigService;

    /** 公开配置（登录页品牌等，无需鉴权） */
    @GetMapping("/public")
    public ApiResponse<AppConfigVO> publicConfig() {
        return ApiResponse.success(appConfigService.getPublic());
    }

    @GetMapping
    public ApiResponse<AppConfigVO> get() {
        return ApiResponse.success(appConfigService.getForAdmin());
    }

    @PutMapping
    @OperLog(title = "系统配置", businessType = OperBusinessType.UPDATE)
    public ApiResponse<AppConfigVO> update(@Valid @RequestBody AppConfigVO request) {
        return ApiResponse.success("保存成功", appConfigService.update(request));
    }

    @PostMapping("/upload")
    @OperLog(title = "系统配置", businessType = OperBusinessType.OTHER)
    public ApiResponse<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String url = appConfigService.uploadAsset(file);
        return ApiResponse.success("上传成功", Map.of("url", url));
    }
}
