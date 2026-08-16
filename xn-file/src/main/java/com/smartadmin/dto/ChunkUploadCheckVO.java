package com.smartadmin.dto;

import lombok.Data;

/** 秒传 / 续传探测结果 */
@Data
public class ChunkUploadCheckVO {

    /** true 表示服务端已存在同指纹文件，可直接秒传 */
    private boolean exists;

    /** exists=true 时给出已存在文件信息（含可访问 URL） */
    private FileInfoVO file;

    /** 存在可续传会话时返回，前端可据此跳过已传分片；否则为空 */
    private ChunkUploadSessionVO session;
}
