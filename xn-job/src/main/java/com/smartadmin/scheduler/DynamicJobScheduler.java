package com.smartadmin.scheduler;

import com.smartadmin.entity.SysJob;
import com.smartadmin.repository.SysJobRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Component;

/**
 * 基于 Quartz 的动态任务调度（支持 misfire / 并发策略）。
 * 对外仍保持原有 reschedule / cancel / runOnce API。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicJobScheduler {

    public static final String JOB_GROUP = "xn-admin-jobs";

    private final Scheduler scheduler;
    private final SysJobRepository jobRepository;
    private final JobInvokeService jobInvokeService;

    @PostConstruct
    public void init() {
        jobRepository.findByStatus(1).forEach(job -> {
            try {
                schedule(job);
            } catch (Exception ex) {
                log.warn("启动时调度任务失败 {}: {}", job.getJobKey(), ex.getMessage());
            }
        });
    }

    public void reschedule(SysJob job) {
        cancel(job.getId());
        if (job.getStatus() != null && job.getStatus() == 1) {
            try {
                schedule(job);
            } catch (Exception ex) {
                log.warn("调度任务失败 {}: {}", job.getJobKey(), ex.getMessage());
            }
        }
    }

    public void cancel(Long jobId) {
        if (jobId == null) {
            return;
        }
        try {
            JobKey key = jobKey(jobId);
            if (scheduler.checkExists(key)) {
                scheduler.deleteJob(key);
            }
        } catch (SchedulerException ex) {
            log.warn("取消任务失败 id={}: {}", jobId, ex.getMessage());
        }
    }

    /** @deprecated 使用 {@link #cancel(Long)}，保留兼容旧调用 */
    @Deprecated
    public void cancel(String jobKey) {
        jobRepository.findAll().stream()
                .filter(j -> jobKey != null && jobKey.equals(j.getJobKey()))
                .findFirst()
                .ifPresent(j -> cancel(j.getId()));
    }

    public void runOnce(SysJob job) {
        jobInvokeService.execute(job);
    }

    private void schedule(SysJob job) throws SchedulerException {
        JobKey key = jobKey(job.getId());
        Class<? extends org.quartz.Job> jobClass = Boolean.TRUE.equals(job.getConcurrent())
                ? ConcurrentQuartzJob.class
                : NonConcurrentQuartzJob.class;

        JobDetail detail = JobBuilder.newJob(jobClass)
                .withIdentity(key)
                .usingJobData(ConcurrentQuartzJob.JOB_ID_KEY, job.getId())
                .storeDurably(false)
                .build();

        CronScheduleBuilder scheduleBuilder = JobMisfirePolicy.apply(
                CronScheduleBuilder.cronSchedule(job.getCron()),
                job.getMisfirePolicy());

        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey(job.getId()))
                .withSchedule(scheduleBuilder)
                .build();

        if (scheduler.checkExists(key)) {
            scheduler.deleteJob(key);
        }
        scheduler.scheduleJob(detail, trigger);
        log.debug("已调度任务 {} cron={} misfire={} concurrent={}",
                job.getJobKey(), job.getCron(),
                JobMisfirePolicy.normalize(job.getMisfirePolicy()),
                job.getConcurrent());
    }

    public static JobKey jobKey(Long jobId) {
        return JobKey.jobKey("job-" + jobId, JOB_GROUP);
    }

    public static TriggerKey triggerKey(Long jobId) {
        return TriggerKey.triggerKey("trigger-" + jobId, JOB_GROUP);
    }
}
