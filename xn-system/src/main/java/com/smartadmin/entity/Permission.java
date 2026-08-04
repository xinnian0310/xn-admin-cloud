package com.smartadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "sys_permission")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PermissionType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Permission parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    private Set<Permission> children = new HashSet<>();

    @Column(length = 200)
    private String path;

    @Column(length = 10)
    private String method;

    /** 前端动作标识：add / edit / view / delete / assign / add-child 等 */
    @Column(length = 50)
    private String action;

    /** Element Plus 图标名，如 Plus / Edit / Delete / View */
    @Column(length = 50)
    private String icon;

    /** 按钮颜色，对应前端 typeColor：primary / success / warning / danger / info / default */
    @Column(length = 20)
    private String buttonColor;

    @Column(nullable = false)
    private Integer sort = 0;

    @Column(nullable = false)
    private Boolean builtIn = false;

    @ManyToMany(mappedBy = "permissions")
    private Set<Role> roles = new HashSet<>();
}
