package com.smartadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@Entity
@Table(name = "sys_dict_data")
public class SysDictData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属字典类型编码，对应 SysDictType.type */
    @Column(nullable = false, length = 100)
    private String dictType;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false, length = 100)
    private String value;

    @Column(nullable = false)
    private Integer sort = 0;

    @Column(nullable = false)
    private Integer status = 1;

    @Column(nullable = false)
    private Boolean isDefault = false;

    /** 标签样式：default/primary/success/warning/danger/info，对应前端 el-tag type */
    @Column(length = 20)
    private String listClass;

    @Column(length = 200)
    private String remark;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp private LocalDateTime updatedAt;
}
