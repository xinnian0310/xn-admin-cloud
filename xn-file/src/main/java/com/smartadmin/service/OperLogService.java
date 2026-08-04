package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.OperLogVO;
import com.smartadmin.dto.PageResult;
import com.smartadmin.entity.OperBusinessType;
import com.smartadmin.entity.SysOperLog;
import com.smartadmin.repository.SysOperLogRepository;
import com.smartadmin.util.ExcelExportUtil;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperLogService {

    private static final int MAX_PARAMS_LENGTH = 2000;
    private static final int EXPORT_LIMIT = 10000;

    private final SysOperLogRepository operLogRepository;
    private final RbacService rbacService;
    private final DataScopeService dataScopeService;

    /** 保存一条操作日志；任何异常都不应影响被拦截业务方法的正常返回/抛出 */
    public void record(
            String title,
            OperBusinessType businessType,
            String operatorName,
            String requestMethod,
            String requestUrl,
            String method,
            String ip,
            String params,
            int status,
            String errorMsg,
            long costTime) {
        try {
            SysOperLog entity = new SysOperLog();
            entity.setTitle(title);
            entity.setBusinessType(businessType);
            entity.setOperatorName(operatorName);
            entity.setRequestMethod(requestMethod);
            entity.setRequestUrl(requestUrl);
            entity.setMethod(method);
            entity.setIp(ip);
            entity.setParams(truncate(params));
            entity.setStatus(status);
            entity.setErrorMsg(truncate(errorMsg, 500));
            entity.setCostTime(costTime);
            operLogRepository.save(entity);
        } catch (Exception e) {
            log.warn("记录操作日志失败：{}", e.getMessage());
        }
    }

    public PageResult<OperLogVO> list(
            int page,
            int size,
            String keyword,
            String businessType,
            Integer status,
            LocalDateTime begin,
            LocalDateTime end) {
        rbacService.checkPermission("menu:system:oper-log");
        DataScopeService.UsernameFilter filter = dataScopeService.resolveUsernameFilter();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        OperBusinessType type = parseBusinessType(businessType);
        Page<SysOperLog> result =
                operLogRepository.search(
                        StringUtils.hasText(keyword) ? keyword.trim() : "",
                        type,
                        status,
                        begin,
                        end,
                        filter.usernames(),
                        filter.unrestricted(),
                        pageable);
        List<OperLogVO> records = result.getContent().stream().map(OperLogVO::from).toList();
        return new PageResult<>(records, result.getTotalElements(), page, size);
    }

    public OperLogVO getById(Long id) {
        rbacService.checkPermission("menu:system:oper-log");
        SysOperLog entity =
                operLogRepository.findById(id).orElseThrow(() -> new BusinessException("操作日志不存在"));
        dataScopeService.assertUsernameAccessible(entity.getOperatorName());
        return OperLogVO.from(entity);
    }

    public byte[] exportExcel(
            String keyword,
            String businessType,
            Integer status,
            LocalDateTime begin,
            LocalDateTime end) {
        rbacService.checkPermission("operlog:export");
        DataScopeService.UsernameFilter filter = dataScopeService.resolveUsernameFilter();
        OperBusinessType type = parseBusinessType(businessType);
        List<SysOperLog> rows =
                operLogRepository.searchAll(
                        StringUtils.hasText(keyword) ? keyword.trim() : "",
                        type,
                        status,
                        begin,
                        end,
                        filter.usernames(),
                        filter.unrestricted());
        if (rows.size() > EXPORT_LIMIT) {
            rows = rows.subList(0, EXPORT_LIMIT);
        }
        return ExcelExportUtil.toXlsx(
                "操作日志",
                List.of("ID", "模块", "业务类型", "操作人", "请求方式", "请求地址", "IP", "状态", "耗时(ms)", "操作时间"),
                rows.stream()
                        .map(
                                r ->
                                        List.of(
                                                String.valueOf(r.getId()),
                                                nullToEmpty(r.getTitle()),
                                                r.getBusinessType() == null
                                                        ? ""
                                                        : r.getBusinessType().name(),
                                                nullToEmpty(r.getOperatorName()),
                                                nullToEmpty(r.getRequestMethod()),
                                                nullToEmpty(r.getRequestUrl()),
                                                nullToEmpty(r.getIp()),
                                                r.getStatus() != null && r.getStatus() == 1
                                                        ? "成功"
                                                        : "失败",
                                                r.getCostTime() == null
                                                        ? ""
                                                        : String.valueOf(r.getCostTime()),
                                                r.getOperTime() == null
                                                        ? ""
                                                        : r.getOperTime().toString()))
                        .toList());
    }

    @Transactional
    public void delete(Long id) {
        rbacService.checkPermission("operlog:delete");
        SysOperLog entity = operLogRepository.findById(id).orElse(null);
        if (entity != null) {
            dataScopeService.assertUsernameAccessible(entity.getOperatorName());
            operLogRepository.deleteById(id);
        }
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        rbacService.checkPermission("operlog:delete");
        int count = 0;
        for (Long id : ids) {
            SysOperLog entity = operLogRepository.findById(id).orElse(null);
            if (entity == null) {
                continue;
            }
            dataScopeService.assertUsernameAccessible(entity.getOperatorName());
            operLogRepository.delete(entity);
            count++;
        }
        return count;
    }

    @Transactional
    public void clean() {
        rbacService.checkPermission("operlog:clean");
        DataScopeService.UsernameFilter filter = dataScopeService.resolveUsernameFilter();
        if (filter.unrestricted()) {
            operLogRepository.deleteAllInBatch();
            return;
        }
        List<SysOperLog> rows =
                operLogRepository.searchAll("", null, null, null, null, filter.usernames(), false);
        operLogRepository.deleteAllInBatch(rows);
    }

    @Transactional
    public int deleteBefore(LocalDateTime before) {
        if (before == null) {
            return 0;
        }
        return operLogRepository.deleteByOperTimeBefore(before);
    }

    private OperBusinessType parseBusinessType(String businessType) {
        if (!StringUtils.hasText(businessType)) {
            return null;
        }
        try {
            return OperBusinessType.valueOf(businessType);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String truncate(String value) {
        return truncate(value, MAX_PARAMS_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
