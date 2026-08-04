package com.smartadmin.dto;

import com.smartadmin.entity.SysDictType;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class DictTypeVO {

    private Long id;
    private String name;
    private String type;
    private Integer status;
    private String remark;
    private Boolean builtIn;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DictTypeVO from(SysDictType entity) {
        DictTypeVO vo = new DictTypeVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setType(entity.getType());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setBuiltIn(entity.getBuiltIn());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
