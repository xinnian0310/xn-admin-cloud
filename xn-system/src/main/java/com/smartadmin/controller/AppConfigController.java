package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.AppConfigVO;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.AppConfigService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;

/** 系统配置：各分区独立读写，分别落到自己的表。 */
@RestController
@RequestMapping("/api/system-config")
@RequiredArgsConstructor
public class AppConfigController {

    private final AppConfigService appConfigService;

    /**
     * 公开配置（登录页品牌等，无需鉴权）。
     *
     * @param client 前端工程 clientId；传入时用 {@code app.clients[client]} 覆盖 name / intro
     */
    @GetMapping("/public")
    public ApiResponse<AppConfigVO> publicConfig(
            @RequestParam(value = "client", required = false) String client) {
        return ApiResponse.success(appConfigService.getPublic(client));
    }

    /** 聚合读取（页面首次加载） */
    @GetMapping
    public ApiResponse<AppConfigVO> get() {
        return ApiResponse.success(appConfigService.getForAdmin());
    }

    @GetMapping("/app")
    public ApiResponse<Object> getApp() {
        return ApiResponse.success(appConfigService.getSection(AppConfigService.SECTION_APP));
    }

    @PutMapping("/app")
    @OperLog(title = "系统配置-应用信息", businessType = OperBusinessType.UPDATE)
    public ApiResponse<AppConfigVO> updateApp(@RequestBody JsonNode request) {
        return ApiResponse.success(
                "保存成功", appConfigService.updateSection(AppConfigService.SECTION_APP, request));
    }

    @GetMapping("/session")
    public ApiResponse<Object> getSession() {
        return ApiResponse.success(appConfigService.getSection(AppConfigService.SECTION_SESSION));
    }

    @PutMapping("/session")
    @OperLog(title = "系统配置-会话策略", businessType = OperBusinessType.UPDATE)
    public ApiResponse<AppConfigVO> updateSession(@RequestBody JsonNode request) {
        return ApiResponse.success(
                "保存成功", appConfigService.updateSection(AppConfigService.SECTION_SESSION, request));
    }

    @GetMapping("/ui")
    public ApiResponse<Object> getUi() {
        return ApiResponse.success(appConfigService.getSection(AppConfigService.SECTION_UI));
    }

    @PutMapping("/ui")
    @OperLog(title = "系统配置-布局与 UI", businessType = OperBusinessType.UPDATE)
    public ApiResponse<AppConfigVO> updateUi(@RequestBody JsonNode request) {
        return ApiResponse.success(
                "保存成功", appConfigService.updateSection(AppConfigService.SECTION_UI, request));
    }

    /** 对象存储：{ items: [{ name, path }] } */
    @GetMapping("/storage")
    public ApiResponse<Object> getStorage() {
        return ApiResponse.success(appConfigService.getSection(AppConfigService.SECTION_STORAGE));
    }

    @PutMapping("/storage")
    @OperLog(title = "系统配置-对象存储", businessType = OperBusinessType.UPDATE)
    public ApiResponse<AppConfigVO> updateStorage(@RequestBody JsonNode request) {
        return ApiResponse.success(
                "保存成功", appConfigService.updateSection(AppConfigService.SECTION_STORAGE, request));
    }

    @GetMapping("/log-retention")
    public ApiResponse<Object> getLogRetention() {
        return ApiResponse.success(
                appConfigService.getSection(AppConfigService.SECTION_LOG_RETENTION));
    }

    @PutMapping("/log-retention")
    @OperLog(title = "系统配置-日志保留", businessType = OperBusinessType.UPDATE)
    public ApiResponse<AppConfigVO> updateLogRetention(@RequestBody JsonNode request) {
        return ApiResponse.success(
                "保存成功",
                appConfigService.updateSection(AppConfigService.SECTION_LOG_RETENTION, request));
    }

    @GetMapping("/sensitive-data")
    public ApiResponse<Object> getSensitiveData() {
        return ApiResponse.success(
                appConfigService.getSection(AppConfigService.SECTION_SENSITIVE_DATA));
    }

    @PutMapping("/sensitive-data")
    @OperLog(title = "系统配置-数据脱敏", businessType = OperBusinessType.UPDATE)
    public ApiResponse<AppConfigVO> updateSensitiveData(@RequestBody JsonNode request) {
        return ApiResponse.success(
                "保存成功",
                appConfigService.updateSection(AppConfigService.SECTION_SENSITIVE_DATA, request));
    }

    @PostMapping("/upload")
    @OperLog(title = "系统配置", businessType = OperBusinessType.OTHER)
    public ApiResponse<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String url = appConfigService.uploadAsset(file);
        return ApiResponse.success("上传成功", Map.of("url", url));
    }
}
