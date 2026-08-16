package com.smartadmin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 业务附件：展示名 + 存储路径。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttachmentItem {

    /** 原始文件名，列表与回显都用它 */
    private String name;

    /** 存储侧路径（MinIO object key / 本地相对路径） */
    private String path;

    /** 字节数；旧数据可能没有 */
    private Long size;

    /** 上传完成时间，展示用 */
    private String uploadedAt;

    public AttachmentItem(String name, String path) {
        this.name = name;
        this.path = path;
    }
}
