package com.smartadmin.dto;

import com.smartadmin.entity.SysDictData;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class DictDataVO {

    private Long id;
    private String dictType;
    private String label;
    private String value;
    private Integer sort;
    private Integer status;
    private Boolean isDefault;
    private String listClass;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DictDataVO from(SysDictData entity) {
        DictDataVO vo = new DictDataVO();
        vo.setId(entity.getId());
        vo.setDictType(entity.getDictType());
        vo.setLabel(entity.getLabel());
        vo.setValue(entity.getValue());
        vo.setSort(entity.getSort());
        vo.setStatus(entity.getStatus());
        vo.setIsDefault(entity.getIsDefault());
        vo.setListClass(entity.getListClass());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
