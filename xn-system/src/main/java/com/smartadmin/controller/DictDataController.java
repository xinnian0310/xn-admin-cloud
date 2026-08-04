package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.DictDataRequest;
import com.smartadmin.dto.DictDataVO;
import com.smartadmin.dto.IdsRequest;
import com.smartadmin.dto.PageResult;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.DictDataService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/dict-data")
@RequiredArgsConstructor
public class DictDataController {

    private final DictDataService dictDataService;

    @GetMapping
    public ApiResponse<PageResult<DictDataVO>> list(
            @RequestParam String dictType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success(dictDataService.list(dictType, page, size, keyword, status));
    }

    /** 供任意业务页面动态取字典数据（下拉选项/标签渲染），全部登录用户可读 */
    @GetMapping("/type/{dictType}")
    public ApiResponse<List<DictDataVO>> byType(@PathVariable String dictType) {
        return ApiResponse.success(dictDataService.listByType(dictType));
    }

    @GetMapping("/{id}")
    public ApiResponse<DictDataVO> detail(@PathVariable Long id) {
        return ApiResponse.success(dictDataService.getById(id));
    }

    @PostMapping
    @OperLog(title = "字典数据", businessType = OperBusinessType.INSERT)
    public ApiResponse<DictDataVO> create(@Valid @RequestBody DictDataRequest request) {
        return ApiResponse.success("创建成功", dictDataService.create(request));
    }

    @PutMapping("/{id}")
    @OperLog(title = "字典数据", businessType = OperBusinessType.UPDATE)
    public ApiResponse<DictDataVO> update(
            @PathVariable Long id, @Valid @RequestBody DictDataRequest request) {
        return ApiResponse.success("更新成功", dictDataService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @OperLog(title = "字典数据", businessType = OperBusinessType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        dictDataService.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/batch-delete")
    @OperLog(title = "字典数据", businessType = OperBusinessType.DELETE)
    public ApiResponse<Map<String, Integer>> batchDelete(@Valid @RequestBody IdsRequest request) {
        int count = dictDataService.batchDelete(request.getIds());
        return ApiResponse.success("删除成功", Map.of("count", count));
    }
}
