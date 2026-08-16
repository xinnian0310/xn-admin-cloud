package com.smartadmin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 业务附件上传结果：仅返回展示文件名与对象路径。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentUploadVO {

    /** 原始文件名，如 报告.pdf */
    private String fileName;

    /** 对象路径（objectKey），如 2026/08/15/11465fcc3d7443daaeb708b9ef6347e6.pdf */
    private String filePath;
}
