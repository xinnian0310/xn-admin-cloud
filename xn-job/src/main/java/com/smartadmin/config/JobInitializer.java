package com.smartadmin.config;

import com.smartadmin.entity.SysJob;
import com.smartadmin.repository.SysJobRepository;
import com.smartadmin.scheduler.DynamicJobScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(9)
@RequiredArgsConstructor
public class JobInitializer implements CommandLineRunner {

    private final SysJobRepository jobRepository;
    private final DynamicJobScheduler dynamicJobScheduler;

    @Override
    @Transactional
    public void run(String... args) {
        ensureDemoJob();
        ensureLogRetentionJob();
    }

    private void ensureDemoJob() {
        if (jobRepository.existsByJobKey("demo-heartbeat")) {
            return;
        }
        SysJob job = new SysJob();
        job.setName("演示心跳任务");
        job.setJobKey("demo-heartbeat");
        job.setCron("0 */5 * * * ?");
        job.setInvokeTarget("demoJob.heartbeat");
        job.setStatus(0);
        job.setRemark("每 5 分钟输出一次心跳日志，默认停用");
        job.setConcurrent(false);
        job.setMisfirePolicy("0");
        jobRepository.save(job);
    }

    private void ensureLogRetentionJob() {
        if (jobRepository.existsByJobKey("log-retention")) {
            return;
        }
        SysJob job = new SysJob();
        job.setName("日志保留清理");
        job.setJobKey("log-retention");
        job.setCron("0 0 2 * * ?");
        job.setInvokeTarget("logRetentionJob.cleanExpired");
        job.setStatus(1);
        job.setRemark("每天凌晨 2 点按系统配置保留天数清理登录/操作/异常日志");
        job.setConcurrent(false);
        job.setMisfirePolicy("0");
        SysJob saved = jobRepository.save(job);
        dynamicJobScheduler.reschedule(saved);
    }
}
