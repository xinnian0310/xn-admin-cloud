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

    @GetMapping
    public ApiResponse<AppConfigVO> get() {
        return ApiResponse.success(appConfigService.getForAdmin());
    }

    /** 保存系统配置。请求体与已存配置做深合并：未提交的字段（如 React 不传 ui.elementPlus、Vue 不传 ui.antd）保留云端原值，各前端只需维护本栈字段。 */
    @PutMapping
    @OperLog(title = "系统配置", businessType = OperBusinessType.UPDATE)
    public ApiResponse<AppConfigVO> update(@RequestBody JsonNode request) {
        return ApiResponse.success("保存成功", appConfigService.update(request));
    }

    @PostMapping("/upload")
    @OperLog(title = "系统配置", businessType = OperBusinessType.OTHER)
    public ApiResponse<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String url = appConfigService.uploadAsset(file);
        return ApiResponse.success("上传成功", Map.of("url", url));
    }
}
