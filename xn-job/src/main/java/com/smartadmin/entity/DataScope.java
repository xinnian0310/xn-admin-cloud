package com.smartadmin.entity;

/**
 * 角色数据权限范围。
 * 多角色取最宽范围：ALL &gt; UNIT_AND_CHILDREN &gt; UNIT &gt; SELF。
 */
public enum DataScope {
    /** 全部数据 */
    ALL,
    /** 本单位及下级（默认） */
    UNIT_AND_CHILDREN,
    /** 仅本单位 */
    UNIT,
    /** 仅本人 */
    SELF
}
