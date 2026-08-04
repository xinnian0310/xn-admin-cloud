package com.smartadmin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "sys_job_log")
public class SysJobLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long jobId;

    @Column(length = 100)
    private String jobName;

    @Column(length = 100)
    private String jobKey;

    @Column(length = 500)
    private String invokeTarget;

    /** SUCCESS / FAIL / SKIP */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 500)
    private String message;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String exceptionInfo;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 耗时毫秒 */
    private Long costMs;
}
