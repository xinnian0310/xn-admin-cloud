package com.smartadmin.entity;

public enum PermissionType {
    /** 菜单/目录 */
    MENU,
    /** 工具栏按钮（xnButton），如新增 */
    BUTTON,
    /** 接口权限 */
    API,
    /** 表格操作列按钮，如编辑、删除、分配权限等行内操作 */
    TABLE_BUTTON
}
