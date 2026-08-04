package com.smartadmin.scheduler;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/** 禁止并发执行的 Quartz Job（对标若依 concurrent=false）。 */
@DisallowConcurrentExecution
public class NonConcurrentQuartzJob implements Job {

    @Autowired private JobInvokeService jobInvokeService;

    @Override
    public void execute(JobExecutionContext context) {
        Long jobId = context.getMergedJobDataMap().getLong(ConcurrentQuartzJob.JOB_ID_KEY);
        jobInvokeService.executeById(jobId);
    }
}
