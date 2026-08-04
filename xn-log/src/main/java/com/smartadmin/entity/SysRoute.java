package com.smartadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "sys_route")
public class SysRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    /** 访问路径，如 /system/roles */
    @Column(length = 200)
    private String path;

    /** 视图目录，如 system/roles，对应 views/system/roles/index.vue */
    @Column(length = 200)
    private String viewPath;

    @Column(length = 50)
    private String icon;

    @Column(length = 100)
    private String permission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private SysRoute parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    private Set<SysRoute> children = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RouteType type = RouteType.MENU;

    @Column(nullable = false)
    private Integer sort = 0;

    @Column(nullable = false)
    private Integer status = 1;

    @Column(nullable = false)
    private Boolean hidden = false;

    @Column(nullable = false)
    private Boolean affix = false;

    /** 是否启用菜单权限控制：关闭后登录用户均可访问，不校验菜单权限 */
    @Column(nullable = false)
    private Boolean permissionControl = false;

    @Column(nullable = false)
    private Boolean builtIn = false;
}
