package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.ColumnMetaVO;
import com.smartadmin.dto.TableCodegenRequest;
import com.smartadmin.dto.TableCodegenVO;
import com.smartadmin.dto.TableInfoVO;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.TableCodegenService;
import com.smartadmin.service.TableMetaService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/codegen")
@RequiredArgsConstructor
public class TableCodegenController {

    private final TableMetaService tableMetaService;
    private final TableCodegenService tableCodegenService;

    @GetMapping("/tables")
    public ApiResponse<List<TableInfoVO>> tables(
            @RequestParam(defaultValue = "true") boolean includeSys) {
        return ApiResponse.success(tableMetaService.listTables(includeSys));
    }

    @GetMapping("/tables/{tableName}/columns")
    public ApiResponse<List<ColumnMetaVO>> columns(@PathVariable String tableName) {
        return ApiResponse.success(tableMetaService.listColumns(tableName));
    }

    @PostMapping("/preview")
    @OperLog(title = "代码生成", businessType = OperBusinessType.OTHER)
    public ApiResponse<TableCodegenVO> preview(@Valid @RequestBody TableCodegenRequest request) {
        // preview 与 generate 相同：权限落库由请求开关控制；默认建议 generate 开启落库
        return ApiResponse.success(tableCodegenService.generate(request));
    }

    @PostMapping("/generate")
    @OperLog(title = "代码生成", businessType = OperBusinessType.OTHER)
    public ApiResponse<TableCodegenVO> generate(@Valid @RequestBody TableCodegenRequest request) {
        return ApiResponse.success("生成成功", tableCodegenService.generate(request));
    }
}
