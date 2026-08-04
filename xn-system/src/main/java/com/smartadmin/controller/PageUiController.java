package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.dto.PageUiConfigVO;
import com.smartadmin.service.PageUiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/page-ui")
@RequiredArgsConstructor
public class PageUiController {

    private final PageUiService pageUiService;

    @GetMapping
    public ApiResponse<PageUiConfigVO> getConfig(@RequestParam String path) {
        return ApiResponse.success(pageUiService.getConfigForCurrentUser(path));
    }
}
