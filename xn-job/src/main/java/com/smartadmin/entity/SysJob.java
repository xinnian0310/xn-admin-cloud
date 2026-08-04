package com.smartadmin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@Entity
@Table(name = "sys_job")
public class SysJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "job_key", nullable = false, unique = true, length = 100)
    private String jobKey;

    @Column(nullable = false, length = 100)
    private String cron;

    @Column(name = "invoke_target", nullable = false, length = 500)
    private String invokeTarget;

    /** 0=停用 1=启用 */
    @Column(nullable = false)
    private Integer status = 0;

    @Column(length = 500)
    private String remark;

    @Column(nullable = false)
    private Boolean concurrent = false;

    /**
     * misfire 策略：0默认 1忽略misfire 2补偿执行一次 3不触发。
     *
     * @see com.smartadmin.scheduler.JobMisfirePolicy
     */
    @Column(name = "misfire_policy", nullable = false, length = 8)
    private String misfirePolicy = "0";

    private LocalDateTime lastRunAt;

    @Column(length = 20)
    private String lastStatus;

    @Column(length = 500)
    private String lastMessage;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp private LocalDateTime updatedAt;
}
