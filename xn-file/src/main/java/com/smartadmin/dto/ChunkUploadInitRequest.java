package com.smartadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** 初始化分片上传入参。分片总数由服务端按 fileSize / chunkSize 计算，不接受客户端传入。 */
@Data
public class ChunkUploadInitRequest {

    @NotBlank(message = "文件指纹不能为空")
    private String fileHash;

    /** sha256 | sha256-tree | meta；为空按 sha256-tree 处理 */
    private String hashAlgo;

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须大于 0")
    private Long fileSize;

    @NotNull(message = "分片大小不能为空")
    @Positive(message = "分片大小必须大于 0")
    private Integer chunkSize;

    private String contentType;
}
