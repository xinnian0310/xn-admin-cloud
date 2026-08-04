package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.SiteContactVO;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.SiteContactService;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/site-contact")
@RequiredArgsConstructor
public class SiteContactController {

    private final SiteContactService siteContactService;

    /** 公开配置（管理端首页 / 官网，无需鉴权） */
    @GetMapping("/public")
    public ApiResponse<SiteContactVO> publicConfig() {
        return ApiResponse.success(siteContactService.getPublic());
    }

    @GetMapping
    public ApiResponse<SiteContactVO> get() {
        return ApiResponse.success(siteContactService.getForAdmin());
    }

    @PutMapping
    @OperLog(title = "联系与捐赠", businessType = OperBusinessType.UPDATE)
    public ApiResponse<SiteContactVO> update(@Valid @RequestBody SiteContactVO request) {
        return ApiResponse.success("保存成功", siteContactService.update(request));
    }

    @PostMapping("/upload")
    @OperLog(title = "联系与捐赠", businessType = OperBusinessType.OTHER)
    public ApiResponse<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String url = siteContactService.uploadQrcode(file);
        return ApiResponse.success("上传成功", Map.of("url", url));
    }
}
