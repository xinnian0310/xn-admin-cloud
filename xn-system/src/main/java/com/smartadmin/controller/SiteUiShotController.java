package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.dto.SiteUiShotVO;
import com.smartadmin.service.SiteUiShotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 官网界面预览截图排序（公开，无需鉴权）。
 *
 * <p>顺序与路由管理菜单树一致；登录页作为入口置顶。
 */
@RestController
@RequestMapping("/api/site-ui-shots")
@RequiredArgsConstructor
public class SiteUiShotController {

    private final SiteUiShotService siteUiShotService;

    @GetMapping("/public")
    public ApiResponse<SiteUiShotVO> publicShots(
            @RequestParam(value = "project", required = false, defaultValue = "vue")
                    String project) {
        return ApiResponse.success(siteUiShotService.getPublic(project));
    }
}
