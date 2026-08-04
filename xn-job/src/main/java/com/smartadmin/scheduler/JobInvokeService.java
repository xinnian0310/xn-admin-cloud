package com.smartadmin.scheduler;

import com.smartadmin.common.BusinessException;
import com.smartadmin.entity.SysJob;
import com.smartadmin.repository.SysJobRepository;
import com.smartadmin.service.JobLogService;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 反射执行 invokeTarget，并写入任务结果 / 执行日志。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobInvokeService {

    private final SysJobRepository jobRepository;
    private final ApplicationContext applicationContext;
    private final JobLogService jobLogService;

    @Transactional
    public void executeById(Long jobId) {
        SysJob job =
                jobRepository
                        .findById(jobId)
                        .orElseThrow(() -> new BusinessException("定时任务不存在: " + jobId));
        execute(job);
    }

    @Transactional
    public void execute(SysJob job) {
        SysJob managed = jobRepository.findById(job.getId()).orElse(job);
        LocalDateTime start = LocalDateTime.now();
        try {
            invokeTarget(managed.getInvokeTarget());
            LocalDateTime end = LocalDateTime.now();
            updateResult(managed, "SUCCESS", "执行成功");
            recordLog(managed, "SUCCESS", "执行成功", null, start, end);
        } catch (Exception ex) {
            log.warn("任务 {} 执行失败：{}", managed.getJobKey(), ex.getMessage());
            LocalDateTime end = LocalDateTime.now();
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            updateResult(managed, "FAIL", cause.getMessage());
            recordLog(
                    managed,
                    "FAIL",
                    cause.getMessage(),
                    JobLogService.stackTraceOf(cause),
                    start,
                    end);
        }
    }

    private void recordLog(
            SysJob job,
            String status,
            String message,
            String exceptionInfo,
            LocalDateTime start,
            LocalDateTime end) {
        long cost = Duration.between(start, end).toMillis();
        jobLogService.record(job, status, message, exceptionInfo, start, end, cost);
    }

    private void invokeTarget(String invokeTarget) throws Exception {
        if (!StringUtils.hasText(invokeTarget) || !invokeTarget.contains(".")) {
            throw new BusinessException("调用目标格式应为 beanName.methodName");
        }
        int dot = invokeTarget.lastIndexOf('.');
        String beanName = invokeTarget.substring(0, dot);
        String methodName = invokeTarget.substring(dot + 1);
        Object bean = applicationContext.getBean(beanName);
        Method method = bean.getClass().getMethod(methodName);
        method.invoke(bean);
    }

    private void updateResult(SysJob job, String status, String message) {
        job.setLastRunAt(LocalDateTime.now());
        job.setLastStatus(status);
        job.setLastMessage(
                StringUtils.hasText(message) && message.length() > 500
                        ? message.substring(0, 500)
                        : message);
        jobRepository.save(job);
    }
}
