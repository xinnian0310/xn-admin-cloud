package com.smartadmin.dto;

import lombok.Data;

@Data
public class UserImportRow {

    private String username;
    private String password;
    private String nickname;
    private String email;
    private String phone;
    /** 角色编码，多个用英文逗号分隔；可空（依赖单位默认角色） */
    private String roleCodes;
    /** 单位编码 */
    private String unitCode;
    /** 岗位编码 */
    private String postCode;
    /** 1 启用 / 0 禁用，空则默认启用 */
    private Integer status;
}
