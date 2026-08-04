package com.smartadmin.scheduler;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/** 允许并发执行的 Quartz Job（由 SpringBeanJobFactory 注入依赖）。 */
public class ConcurrentQuartzJob implements Job {

    public static final String JOB_ID_KEY = "jobId";

    @Autowired
    private JobInvokeService jobInvokeService;

    @Override
    public void execute(JobExecutionContext context) {
        Long jobId = context.getMergedJobDataMap().getLong(JOB_ID_KEY);
        jobInvokeService.executeById(jobId);
    }
}
