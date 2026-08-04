package com.smartadmin.dto;

import lombok.Data;

@Data
public class FileInfoVO {

    private Long id;
    private String path;
    private String name;
    private String storedName;
    private String extension;
    private String contentType;
    private long size;
    private boolean directory;
    private String lastModified;

    /** local | minio */
    private String storage;

    private String bucket;

    /** 可直接访问的文件 URL */
    private String url;

    /** kkFileView 在线预览地址；未启用或不支持该类型时为空 */
    private String previewUrl;

    private String uploader;
    private String prefix;
}
