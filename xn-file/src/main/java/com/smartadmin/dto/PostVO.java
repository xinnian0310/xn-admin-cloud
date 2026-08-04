package com.smartadmin.dto;

import com.smartadmin.entity.SysPost;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class PostVO {

    private Long id;
    private String code;
    private String name;
    private Integer sort;
    private Integer status;
    private String remark;
    private Boolean builtIn;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PostVO from(SysPost entity) {
        PostVO vo = new PostVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setBuiltIn(entity.getBuiltIn());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
