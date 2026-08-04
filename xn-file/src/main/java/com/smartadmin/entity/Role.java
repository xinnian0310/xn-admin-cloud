package com.smartadmin.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sys_role")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 200)
    private String description;

    @Column(nullable = false)
    private Integer status = 1;

    @Column(nullable = false)
    private Boolean builtIn = false;

    /** 数据权限范围：ALL / UNIT_AND_CHILDREN / UNIT / SELF。 默认本单位及下级；超级管理员在解析时强制 ALL。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DataScope dataScope = DataScope.UNIT_AND_CHILDREN;

    @ManyToMany(mappedBy = "roles")
    private Set<User> users = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "sys_role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();
}
