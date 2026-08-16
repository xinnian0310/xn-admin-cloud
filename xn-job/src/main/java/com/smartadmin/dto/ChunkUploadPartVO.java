package com.smartadmin.dto;

import lombok.Data;

/** 单个分片上传结果 */
@Data
public class ChunkUploadPartVO {

    /** 分片下标（0 起） */
    private int chunkIndex;

    /** 存储侧返回的分片 ETag；本地存储为空 */
    private String etag;

    /** 该分片实际接收字节数 */
    private long size;

    private int totalChunks;
}
