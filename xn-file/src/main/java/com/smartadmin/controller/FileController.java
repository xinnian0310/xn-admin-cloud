package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.FileBrowseVO;
import com.smartadmin.dto.FileDeleteRequest;
import com.smartadmin.dto.FileInfoVO;
import com.smartadmin.dto.FileTreeNodeVO;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.FileManageService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileManageService fileManageService;

    @GetMapping
    public ApiResponse<List<FileInfoVO>> list(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(fileManageService.list(keyword));
    }

    /** 按前缀浏览当前层目录与文件（MinIO 路径树右侧列表） */
    @GetMapping("/browse")
    public ApiResponse<FileBrowseVO> browse(
            @RequestParam(required = false, defaultValue = "") String prefix,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(fileManageService.browse(prefix, keyword));
    }

    /** 左侧目录树（仅文件夹） */
    @GetMapping("/tree")
    public ApiResponse<FileTreeNodeVO> tree() {
        return ApiResponse.success(fileManageService.tree());
    }

    @PostMapping("/upload")
    @OperLog(title = "文件管理", businessType = OperBusinessType.IMPORT)
    public ApiResponse<FileInfoVO> upload(
            @RequestParam("file") MultipartFile file, @RequestParam(required = false) String prefix)
            throws IOException {
        return ApiResponse.success(fileManageService.upload(file, prefix));
    }

    @PostMapping("/mkdir")
    @OperLog(title = "文件管理", businessType = OperBusinessType.INSERT)
    public ApiResponse<Void> mkdir(@RequestBody Map<String, String> body) throws IOException {
        fileManageService.mkdir(body.get("path"));
        return ApiResponse.success("目录已创建", null);
    }

    @DeleteMapping
    @OperLog(title = "文件管理", businessType = OperBusinessType.DELETE)
    public ApiResponse<Void> delete(@Valid @RequestBody FileDeleteRequest request)
            throws IOException {
        fileManageService.delete(request.getPath());
        return ApiResponse.success("删除成功", null);
    }
}
