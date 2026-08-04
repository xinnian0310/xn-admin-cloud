package com.smartadmin.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

/** 权限内容注册表：供前端做接口/按钮的“已登记”校验 */
@Data
@AllArgsConstructor
public class ApiRegistryVO {
    /** 已登记的接口签名（API 类型权限） */
    private List<ApiSignatureVO> apis;

    /** 全部权限编码（菜单/按钮/表格按钮/接口），供 v-permission 开发态校验 */
    private List<String> codes;
}
