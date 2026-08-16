package com.smartadmin.controller;

import com.smartadmin.common.ApiResponse;
import com.smartadmin.common.OperLog;
import com.smartadmin.dto.ChunkUploadCheckRequest;
import com.smartadmin.dto.ChunkUploadCheckVO;
import com.smartadmin.dto.ChunkUploadInitRequest;
import com.smartadmin.dto.ChunkUploadPartVO;
import com.smartadmin.dto.ChunkUploadSessionVO;
import com.smartadmin.dto.FileInfoVO;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.service.ChunkUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 大文件分片上传。小文件仍走 {@code POST /api/files/upload} 单请求直传，由前端按阈值自行选择。
 *
 * <p>暂停 / 继续是纯客户端行为（停止或恢复派发分片请求），故不提供对应接口。
 */
@RestController
@RequestMapping("/api/files/chunk")
@RequiredArgsConstructor
public class ChunkUploadController {

    private final ChunkUploadService chunkUploadService;

    /** 秒传探测：命中同指纹文件直接返回其地址；否则顺带返回可续传的会话 */
    @PostMapping("/check")
    public ApiResponse<ChunkUploadCheckVO> check(
            @Valid @RequestBody ChunkUploadCheckRequest request) {
        return ApiResponse.success(chunkUploadService.check(request));
    }

    /** 初始化上传；同一用户重复初始化同一文件会复用未完成会话 */
    @PostMapping("/init")
    public ApiResponse<ChunkUploadSessionVO> init(
            @Valid @RequestBody ChunkUploadInitRequest request) {
        return ApiResponse.success(chunkUploadService.init(request));
    }

    /** 会话状态与已上传分片清单，断点续传据此跳过已传分片 */
    @GetMapping("/{uploadId}/status")
    public ApiResponse<ChunkUploadSessionVO> status(@PathVariable String uploadId) {
        return ApiResponse.success(chunkUploadService.status(uploadId));
    }

    /** 上传单个分片；同下标重复上传视为覆盖，可安全重试 */
    @PostMapping("/{uploadId}/part")
    public ApiResponse<ChunkUploadPartVO> uploadPart(
            @PathVariable String uploadId,
            @RequestParam int chunkIndex,
            @RequestParam(required = false) String chunkHash,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(
                chunkUploadService.uploadPart(uploadId, chunkIndex, chunkHash, file));
    }

    /** 合并分片并登记文件元数据 */
    @PostMapping("/{uploadId}/complete")
    @OperLog(title = "文件管理", businessType = OperBusinessType.IMPORT)
    public ApiResponse<FileInfoVO> complete(@PathVariable String uploadId) {
        return ApiResponse.success(chunkUploadService.complete(uploadId));
    }

    /** 取消上传并清理已上传分片 */
    @DeleteMapping("/{uploadId}")
    @OperLog(title = "文件管理", businessType = OperBusinessType.DELETE)
    public ApiResponse<Void> cancel(@PathVariable String uploadId) {
        chunkUploadService.cancel(uploadId);
        return ApiResponse.success("已取消上传", null);
    }
}
