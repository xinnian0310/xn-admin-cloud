package com.smartadmin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiSignatureVO {
    private String method;
    private String path;
}
