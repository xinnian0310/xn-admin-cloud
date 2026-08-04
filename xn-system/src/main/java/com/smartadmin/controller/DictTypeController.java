package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.DictTypeRequest;
import com.smartadmin.dto.DictTypeVO;
import com.smartadmin.dto.IdsRequest;
import com.smartadmin.dto.PageResult;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.DictTypeService;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dict-types")
@RequiredArgsConstructor
public class DictTypeController {

    private final DictTypeService dictTypeService;

    @GetMapping
    public ApiResponse<PageResult<DictTypeVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success(dictTypeService.list(page, size, keyword, status));
    }

    @GetMapping("/options")
    public ApiResponse<List<DictTypeVO>> options() {
        return ApiResponse.success(dictTypeService.listOptions());
    }

    @GetMapping("/{id}")
    public ApiResponse<DictTypeVO> detail(@PathVariable Long id) {
        return ApiResponse.success(dictTypeService.getById(id));
    }

    @PostMapping
    @OperLog(title = "字典类型", businessType = OperBusinessType.INSERT)
    public ApiResponse<DictTypeVO> create(@Valid @RequestBody DictTypeRequest request) {
        return ApiResponse.success("创建成功", dictTypeService.create(request));
    }

    @PutMapping("/{id}")
    @OperLog(title = "字典类型", businessType = OperBusinessType.UPDATE)
    public ApiResponse<DictTypeVO> update(@PathVariable Long id, @Valid @RequestBody DictTypeRequest request) {
        return ApiResponse.success("更新成功", dictTypeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @OperLog(title = "字典类型", businessType = OperBusinessType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        dictTypeService.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/batch-delete")
    @OperLog(title = "字典类型", businessType = OperBusinessType.DELETE)
    public ApiResponse<Map<String, Integer>> batchDelete(@Valid @RequestBody IdsRequest request) {
        int count = dictTypeService.batchDelete(request.getIds());
        return ApiResponse.success("删除成功", Map.of("count", count));
    }
}
