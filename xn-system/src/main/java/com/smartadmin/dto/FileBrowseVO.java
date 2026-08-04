package com.smartadmin.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class FileBrowseVO {

    /** minio | local */
    private String storage;

    /** 当前前缀，如 files/docs/ ，根为 "" */
    private String prefix;

    private List<FileInfoVO> dirs = new ArrayList<>();
    private List<FileInfoVO> files = new ArrayList<>();
}
