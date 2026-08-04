package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.JobLogVO;
import com.smartadmin.dto.PageResult;
import com.smartadmin.entity.SysJob;
import com.smartadmin.entity.SysJobLog;
import com.smartadmin.repository.SysJobLogRepository;
import com.smartadmin.util.ExcelExportUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobLogService {

    private static final int EXPORT_LIMIT = 10000;
    private static final int MAX_EXCEPTION_LENGTH = 4000;

    private final SysJobLogRepository jobLogRepository;
    private final RbacService rbacService;

    public void record(SysJob job, String status, String message, String exceptionInfo,
                       LocalDateTime startTime, LocalDateTime endTime, long costMs) {
        try {
            SysJobLog entity = new SysJobLog();
            entity.setJobId(job.getId());
            entity.setJobName(job.getName());
            entity.setJobKey(job.getJobKey());
            entity.setInvokeTarget(job.getInvokeTarget());
            entity.setStatus(status);
            entity.setMessage(truncate(message, 500));
            entity.setExceptionInfo(truncate(exceptionInfo, MAX_EXCEPTION_LENGTH));
            entity.setStartTime(startTime);
            entity.setEndTime(endTime);
            entity.setCostMs(costMs);
            jobLogRepository.save(entity);
        } catch (Exception e) {
            log.warn("记录任务执行日志失败：{}", e.getMessage());
        }
    }

    public static String stackTraceOf(Throwable ex) {
        if (ex == null) {
            return null;
        }
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    public PageResult<JobLogVO> list(int page, int size, String keyword, Long jobId, String status,
                                     LocalDateTime begin, LocalDateTime end) {
        rbacService.checkPermission("menu:system:job-log");
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<SysJobLog> result = jobLogRepository.search(
                StringUtils.hasText(keyword) ? keyword.trim() : "",
                jobId,
                StringUtils.hasText(status) ? status.trim() : null,
                begin, end, pageable);
        List<JobLogVO> records = result.getContent().stream().map(JobLogVO::from).toList();
        return new PageResult<>(records, result.getTotalElements(), page, size);
    }

    public JobLogVO getById(Long id) {
        rbacService.checkPermission("menu:system:job-log");
        return JobLogVO.from(findLog(id));
    }

    public byte[] exportExcel(String keyword, Long jobId, String status,
                            LocalDateTime begin, LocalDateTime end) {
        rbacService.checkPermission("joblog:export");
        List<SysJobLog> rows = jobLogRepository.searchAll(
                StringUtils.hasText(keyword) ? keyword.trim() : "",
                jobId,
                StringUtils.hasText(status) ? status.trim() : null,
                begin, end);
        if (rows.size() > EXPORT_LIMIT) {
            rows = rows.subList(0, EXPORT_LIMIT);
        }
        return ExcelExportUtil.toXlsx(
                "任务日志",
                List.of("ID", "任务名称", "任务标识", "调用目标", "状态", "消息", "开始时间", "结束时间", "耗时(ms)"),
                rows.stream().map(r -> List.of(
                        String.valueOf(r.getId()),
                        nullToEmpty(r.getJobName()),
                        nullToEmpty(r.getJobKey()),
                        nullToEmpty(r.getInvokeTarget()),
                        nullToEmpty(r.getStatus()),
                        nullToEmpty(r.getMessage()),
                        r.getStartTime() == null ? "" : r.getStartTime().toString(),
                        r.getEndTime() == null ? "" : r.getEndTime().toString(),
                        r.getCostMs() == null ? "" : String.valueOf(r.getCostMs())
                )).toList());
    }

    @Transactional
    public void delete(Long id) {
        rbacService.checkPermission("joblog:delete");
        if (jobLogRepository.existsById(id)) {
            jobLogRepository.deleteById(id);
        }
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        rbacService.checkPermission("joblog:delete");
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return jobLogRepository.deleteByIdIn(ids);
    }

    @Transactional
    public void clean() {
        rbacService.checkPermission("joblog:clean");
        jobLogRepository.deleteAllInBatch();
    }

    @Transactional
    public int deleteBefore(LocalDateTime before) {
        return jobLogRepository.deleteByStartTimeBefore(before);
    }

    private SysJobLog findLog(Long id) {
        return jobLogRepository.findById(id)
                .orElseThrow(() -> new BusinessException("任务日志不存在"));
    }

    private static String truncate(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
