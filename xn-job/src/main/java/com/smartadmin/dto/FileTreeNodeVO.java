package com.smartadmin.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class FileTreeNodeVO {

    private String id;
    private String label;
    private String path;
    private List<FileTreeNodeVO> children = new ArrayList<>();
}
