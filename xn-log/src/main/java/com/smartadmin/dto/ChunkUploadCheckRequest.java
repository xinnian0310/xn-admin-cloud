package com.smartadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** 秒传 / 续传探测入参 */
@Data
public class ChunkUploadCheckRequest {

    @NotBlank(message = "文件指纹不能为空")
    private String fileHash;

    /** sha256 | sha256-tree | meta；为空按 sha256-tree 处理。meta 不参与秒传 */
    private String hashAlgo;

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须大于 0")
    private Long fileSize;

    @NotNull(message = "分片大小不能为空")
    @Positive(message = "分片大小必须大于 0")
    private Integer chunkSize;
}
