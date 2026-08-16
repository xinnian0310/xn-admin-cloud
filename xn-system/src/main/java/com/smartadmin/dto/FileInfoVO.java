package com.smartadmin.dto;

import lombok.Data;

@Data
public class FileInfoVO {

    private Long id;

    /** 存储侧路径（MinIO object key / 本地相对路径） */
    private String path;

    /** 原始文件名，列表与回显都用它 */
    private String name;

    private String extension;
    private String contentType;
    private long size;
    private boolean directory;
    private String lastModified;

    /** local | minio */
    private String storage;

    /** 可直接访问的文件 URL */
    private String url;

    /** kkFileView 在线预览地址；未启用或不支持该类型时为空 */
    private String previewUrl;

    private String uploader;
    private String prefix;
}
