package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.JobRequest;
import com.smartadmin.dto.JobVO;
import com.smartadmin.dto.PageResult;
import com.smartadmin.entity.SysJob;
import com.smartadmin.repository.SysJobRepository;
import com.smartadmin.scheduler.DynamicJobScheduler;
import com.smartadmin.scheduler.JobMisfirePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobService {

    private final SysJobRepository jobRepository;
    private final RbacService rbacService;
    private final DynamicJobScheduler dynamicJobScheduler;

    public PageResult<JobVO> list(int page, int size, String keyword, Integer status) {
        rbacService.checkPermission("job:view");
        Page<SysJob> result = jobRepository.search(
                StringUtils.hasText(keyword) ? keyword.trim() : "",
                status,
                PageRequest.of(page, size)
        );
        List<JobVO> records = result.getContent().stream().map(JobVO::from).toList();
        return new PageResult<>(records, result.getTotalElements(), page, size);
    }

    public JobVO getById(Long id) {
        rbacService.checkPermission("job:view");
        return JobVO.from(findJob(id));
    }

    @Transactional
    public JobVO create(JobRequest request) {
        rbacService.checkPermission("job:create");
        if (jobRepository.existsByJobKey(request.getJobKey().trim())) {
            throw new BusinessException("任务标识已存在");
        }
        SysJob job = new SysJob();
        applyRequest(job, request);
        job.setStatus(request.getStatus() != null ? request.getStatus() : 0);
        SysJob saved = jobRepository.save(job);
        dynamicJobScheduler.reschedule(saved);
        return JobVO.from(saved);
    }

    @Transactional
    public JobVO update(Long id, JobRequest request) {
        rbacService.checkPermission("job:update");
        SysJob job = findJob(id);
        if (!job.getJobKey().equals(request.getJobKey().trim())
                && jobRepository.existsByJobKey(request.getJobKey().trim())) {
            throw new BusinessException("任务标识已存在");
        }
        applyRequest(job, request);
        if (request.getStatus() != null) {
            job.setStatus(request.getStatus());
        }
        SysJob saved = jobRepository.save(job);
        dynamicJobScheduler.reschedule(saved);
        return JobVO.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        rbacService.checkPermission("job:delete");
        SysJob job = findJob(id);
        dynamicJobScheduler.cancel(job.getId());
        jobRepository.delete(job);
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        rbacService.checkPermission("job:delete");
        for (Long id : ids) {
            SysJob job = findJob(id);
            dynamicJobScheduler.cancel(job.getId());
        }
        jobRepository.deleteAllByIdInBatch(ids);
        return ids.size();
    }

    @Transactional
    public JobVO changeStatus(Long id, Integer status) {
        rbacService.checkPermission("job:update");
        SysJob job = findJob(id);
        job.setStatus(status);
        SysJob saved = jobRepository.save(job);
        dynamicJobScheduler.reschedule(saved);
        return JobVO.from(saved);
    }

    public JobVO runOnce(Long id) {
        rbacService.checkPermission("job:run");
        SysJob job = findJob(id);
        dynamicJobScheduler.runOnce(job);
        return JobVO.from(jobRepository.findById(id).orElse(job));
    }

    private void applyRequest(SysJob job, JobRequest request) {
        job.setName(request.getName().trim());
        job.setJobKey(request.getJobKey().trim());
        job.setCron(request.getCron().trim());
        job.setInvokeTarget(request.getInvokeTarget().trim());
        job.setRemark(request.getRemark());
        job.setConcurrent(request.getConcurrent() != null ? request.getConcurrent() : false);
        job.setMisfirePolicy(JobMisfirePolicy.normalize(request.getMisfirePolicy()));
    }

    private SysJob findJob(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new BusinessException("定时任务不存在"));
    }
}
