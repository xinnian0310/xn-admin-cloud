package com.smartadmin.entity;

/** 分片上传会话状态 */
public enum UploadSessionStatus {

    /** 已初始化，分片可继续上传 */
    UPLOADING,

    /** 分片已合并，对象已落地 */
    COMPLETED,

    /** 已取消，底层分片已清理 */
    ABORTED
}
