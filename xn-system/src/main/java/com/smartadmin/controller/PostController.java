package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.IdsRequest;
import com.smartadmin.dto.ImportResultVO;
import com.smartadmin.dto.PageResult;
import com.smartadmin.dto.PostImportRow;
import com.smartadmin.dto.PostRequest;
import com.smartadmin.dto.PostVO;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.PostService;
import com.smartadmin.util.ExcelHttpResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    public ApiResponse<PageResult<PostVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success(postService.list(page, size, keyword, status));
    }

    @GetMapping("/options")
    public ApiResponse<List<PostVO>> options() {
        return ApiResponse.success(postService.listOptions());
    }

    @GetMapping("/export")
    @OperLog(title = "岗位管理", businessType = OperBusinessType.EXPORT)
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return ExcelHttpResponse.xlsx(postService.exportExcel(keyword, status), "posts.xlsx");
    }

    @GetMapping("/{id}")
    public ApiResponse<PostVO> detail(@PathVariable Long id) {
        return ApiResponse.success(postService.getById(id));
    }

    @PostMapping
    @OperLog(title = "岗位管理", businessType = OperBusinessType.INSERT)
    public ApiResponse<PostVO> create(@Valid @RequestBody PostRequest request) {
        return ApiResponse.success("创建成功", postService.create(request));
    }

    @PutMapping("/{id}")
    @OperLog(title = "岗位管理", businessType = OperBusinessType.UPDATE)
    public ApiResponse<PostVO> update(@PathVariable Long id, @Valid @RequestBody PostRequest request) {
        return ApiResponse.success("更新成功", postService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @OperLog(title = "岗位管理", businessType = OperBusinessType.DELETE)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/batch-delete")
    @OperLog(title = "岗位管理", businessType = OperBusinessType.DELETE)
    public ApiResponse<Map<String, Integer>> batchDelete(@Valid @RequestBody IdsRequest request) {
        int count = postService.batchDelete(request.getIds());
        return ApiResponse.success("删除成功", Map.of("count", count));
    }

    @PutMapping("/{id}/status")
    @OperLog(title = "岗位管理", businessType = OperBusinessType.UPDATE)
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        postService.updateStatus(id, status);
        return ApiResponse.success("状态已更新", null);
    }

    @PostMapping("/import")
    @OperLog(title = "岗位管理", businessType = OperBusinessType.IMPORT)
    public ApiResponse<ImportResultVO> importPosts(@RequestBody List<PostImportRow> rows) {
        return ApiResponse.success("导入完成", postService.importPosts(rows));
    }

}
