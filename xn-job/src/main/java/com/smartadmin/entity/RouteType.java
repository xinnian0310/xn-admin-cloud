package com.smartadmin.entity;

public enum RouteType {
    /** 目录：仅分组，不注册页面 */
    DIR,
    /** 菜单：本地 Vue 页面 */
    MENU,
    /** 外部链接：主内容区内嵌 iframe */
    LINK
}
