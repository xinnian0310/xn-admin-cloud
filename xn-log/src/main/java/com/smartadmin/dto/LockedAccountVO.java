package com.smartadmin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LockedAccountVO {

    private String username;
    /** 剩余锁定秒数 */
    private Long remainSeconds;
}
