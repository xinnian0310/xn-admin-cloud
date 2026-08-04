package com.smartadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "sys_login_log")
public class SysLoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(length = 50)
    private String ip;

    @Column(length = 300)
    private String userAgent;

    /** 1 成功 / 0 失败 */
    @Column(nullable = false)
    private Integer status = 1;

    @Column(length = 200)
    private String message;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime loginTime;
}
