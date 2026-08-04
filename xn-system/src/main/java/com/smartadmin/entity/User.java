package com.smartadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "sys_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(length = 50)
    private String nickname;

    @Column(length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    /** 头像 URL，如 /uploads/avatars/xxx.png */
    @Column(length = 500)
    private String avatar;

    @Column(nullable = false)
    private Integer status = 1;

    /** @deprecated 冗余字段，v1.3 移除，与 roles 双写 */
    @Column(nullable = false, length = 20)
    private String role = "USER";

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "sys_user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    /** 所属单位 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private SysUnit unit;

    /** 岗位 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private SysPost post;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /** 最近修改密码时间；空表示历史账号未记录，过期策略对其豁免 */
    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    /** 下次登录强制修改密码（管理员新建/重置时按策略置位） */
    @Column(name = "pwd_force_change", nullable = false)
    private Boolean pwdForceChange = false;

    /** 软删除时间；非空表示已在回收站 */
    private LocalDateTime deletedAt;
}
