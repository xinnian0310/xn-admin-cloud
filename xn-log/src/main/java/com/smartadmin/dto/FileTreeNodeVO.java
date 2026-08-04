package com.smartadmin.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FileTreeNodeVO {

    private String id;
    private String label;
    private String path;
    private List<FileTreeNodeVO> children = new ArrayList<>();
}
