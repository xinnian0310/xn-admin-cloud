package com.smartadmin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@Entity
@Table(name = "sys_exception_log")
public class SysExceptionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 10)
    private String requestMethod;

    @Column(length = 500)
    private String requestUrl;

    @Column(length = 500)
    private String method;

    @Column(length = 500)
    private String className;

    @Column(length = 500)
    private String exceptionName;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String message;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String stackTrace;

    @Column(length = 100)
    private String operatorName;

    @Column(length = 64)
    private String ip;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
