package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.common.WebUtils;
import com.smartadmin.dto.ExceptionLogVO;
import com.smartadmin.dto.PageResult;
import com.smartadmin.entity.SysExceptionLog;
import com.smartadmin.repository.SysExceptionLogRepository;
import com.smartadmin.util.ExcelExportUtil;
import jakarta.servlet.http.HttpServletRequest;
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
public class ExceptionLogService {

    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_STACK_LENGTH = 10000;
    private static final int EXPORT_LIMIT = 10000;

    private final SysExceptionLogRepository exceptionLogRepository;
    private final RbacService rbacService;
    private final DataScopeService dataScopeService;

    public void record(Exception ex) {
        try {
            HttpServletRequest request = WebUtils.getCurrentRequest();
            SysExceptionLog entity = new SysExceptionLog();
            entity.setRequestMethod(request != null ? request.getMethod() : null);
            entity.setRequestUrl(request != null ? request.getRequestURI() : null);
            entity.setMethod(null);
            entity.setClassName(ex.getClass().getName());
            entity.setExceptionName(ex.getClass().getSimpleName());
            entity.setMessage(truncate(ex.getMessage(), MAX_MESSAGE_LENGTH));
            entity.setStackTrace(truncate(stackTrace(ex), MAX_STACK_LENGTH));
            entity.setOperatorName(safeCurrentUsername());
            entity.setIp(WebUtils.getClientIp());
            exceptionLogRepository.save(entity);
        } catch (Exception e) {
            log.warn("记录异常日志失败：{}", e.getMessage());
        }
    }

    public PageResult<ExceptionLogVO> list(int page, int size, String keyword,
                                            LocalDateTime begin, LocalDateTime end) {
        rbacService.checkPermission("menu:system:exception-log");
        DataScopeService.UsernameFilter filter = dataScopeService.resolveUsernameFilter();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<SysExceptionLog> result = exceptionLogRepository.search(
                StringUtils.hasText(keyword) ? keyword.trim() : "",
                begin, end,
                filter.usernames(), filter.unrestricted(),
                pageable);
        List<ExceptionLogVO> records = result.getContent().stream().map(ExceptionLogVO::from).toList();
        return new PageResult<>(records, result.getTotalElements(), page, size);
    }

    public ExceptionLogVO getById(Long id) {
        rbacService.checkPermission("menu:system:exception-log");
        SysExceptionLog entity = exceptionLogRepository.findById(id)
                .orElseThrow(() -> new BusinessException("异常日志不存在"));
        dataScopeService.assertUsernameAccessible(entity.getOperatorName());
        return ExceptionLogVO.from(entity);
    }

    public byte[] exportExcel(String keyword, LocalDateTime begin, LocalDateTime end) {
        rbacService.checkPermission("exlog:export");
        DataScopeService.UsernameFilter filter = dataScopeService.resolveUsernameFilter();
        List<SysExceptionLog> rows = exceptionLogRepository.searchAll(
                StringUtils.hasText(keyword) ? keyword.trim() : "",
                begin, end,
                filter.usernames(), filter.unrestricted());
        if (rows.size() > EXPORT_LIMIT) {
            rows = rows.subList(0, EXPORT_LIMIT);
        }
        return ExcelExportUtil.toXlsx(
                "异常日志",
                List.of("ID", "异常", "请求方式", "请求地址", "操作人", "IP", "消息", "发生时间"),
                rows.stream().map(r -> List.of(
                        String.valueOf(r.getId()),
                        nullToEmpty(r.getExceptionName()),
                        nullToEmpty(r.getRequestMethod()),
                        nullToEmpty(r.getRequestUrl()),
                        nullToEmpty(r.getOperatorName()),
                        nullToEmpty(r.getIp()),
                        nullToEmpty(r.getMessage()),
                        r.getCreatedAt() == null ? "" : r.getCreatedAt().toString()
                )).toList());
    }

    @Transactional
    public void delete(Long id) {
        rbacService.checkPermission("exlog:delete");
        SysExceptionLog entity = exceptionLogRepository.findById(id).orElse(null);
        if (entity != null) {
            dataScopeService.assertUsernameAccessible(entity.getOperatorName());
            exceptionLogRepository.deleteById(id);
        }
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        rbacService.checkPermission("exlog:delete");
        int count = 0;
        for (Long id : ids) {
            SysExceptionLog entity = exceptionLogRepository.findById(id).orElse(null);
            if (entity == null) {
                continue;
            }
            dataScopeService.assertUsernameAccessible(entity.getOperatorName());
            exceptionLogRepository.delete(entity);
            count++;
        }
        return count;
    }

    @Transactional
    public void clean() {
        rbacService.checkPermission("exlog:clean");
        DataScopeService.UsernameFilter filter = dataScopeService.resolveUsernameFilter();
        if (filter.unrestricted()) {
            exceptionLogRepository.deleteAllInBatch();
            return;
        }
        List<SysExceptionLog> rows = exceptionLogRepository.searchAll(
                "", null, null, filter.usernames(), false);
        exceptionLogRepository.deleteAllInBatch(rows);
    }

    @Transactional
    public int deleteBefore(LocalDateTime before) {
        if (before == null) {
            return 0;
        }
        return exceptionLogRepository.deleteByCreatedAtBefore(before);
    }

    private String safeCurrentUsername() {
        try {
            return RbacService.currentUsername();
        } catch (Exception e) {
            return null;
        }
    }

    private String stackTrace(Exception ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
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
