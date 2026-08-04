package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.TableColumnConfigRequest;
import com.smartadmin.dto.TableColumnConfigVO;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.TableColumnConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/table-columns")
@RequiredArgsConstructor
public class TableColumnConfigController {

    private final TableColumnConfigService tableColumnConfigService;

    @GetMapping
    public ApiResponse<TableColumnConfigVO> get(@RequestParam(value = "tableKey") String tableKey) {
        return ApiResponse.success(tableColumnConfigService.getForCurrentUser(tableKey));
    }

    @PutMapping
    @OperLog(title = "表格列配置", businessType = OperBusinessType.UPDATE)
    public ApiResponse<TableColumnConfigVO> save(
            @Valid @RequestBody TableColumnConfigRequest request) {
        return ApiResponse.success("保存成功", tableColumnConfigService.saveForCurrentUser(request));
    }
}
