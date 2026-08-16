package com.smartadmin.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 分片上传会话视图：已上传分片清单始终以存储侧为准 */
@Data
public class ChunkUploadSessionVO {

    private String uploadId;
    private String fileName;
    private long fileSize;
    private int chunkSize;
    private int totalChunks;

    /** 已上传分片下标（0 起） */
    private List<Integer> uploadedChunks = new ArrayList<>();

    /** 已上传字节数（按存储侧实际分片大小累加） */
    private long uploadedBytes;

    /** UPLOADING | COMPLETED | ABORTED */
    private String status;

    /** minio | local */
    private String storage;

    /** 服务端要求的最小分片大小（MinIO 原生 multipart 限制），前端可据此校正配置 */
    private int minChunkSize;
}
