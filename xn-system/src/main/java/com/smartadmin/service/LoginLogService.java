package com.smartadmin.service;

import com.smartadmin.dto.LoginLogVO;
import com.smartadmin.dto.PageResult;
import com.smartadmin.entity.SysLoginLog;
import com.smartadmin.repository.SysLoginLogRepository;
import com.smartadmin.util.ExcelExportUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private static final int EXPORT_LIMIT = 10000;

    private final SysLoginLogRepository loginLogRepository;
    private final RbacService rbacService;
    private final DataScopeService dataScopeService;

    /** 记录一次登录尝试；任何异常都不应影响登录主流程 */
    public void record(String username, String ip, String userAgent, int status, String message) {
        try {
            SysLoginLog log = new SysLoginLog();
            log.setUsername(username);
            log.setIp(ip);
            log.setUserAgent(userAgent);
            log.setStatus(status);
            log.setMessage(message);
            loginLogRepository.save(log);
        } catch (Exception e) {
            log.warn("记录登录日志失败：{}", e.getMessage());
        }
    }

    public PageResult<LoginLogVO> list(int page, int size, String keyword, Integer status,
                                        LocalDateTime begin, LocalDateTime end) {
        rbacService.checkPermission("menu:system:login-log");
        DataScopeService.UsernameFilter filter = dataScopeService.resolveUsernameFilter();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<SysLoginLog> result = loginLogRepository.search(
                StringUtils.hasText(keyword) ? keyword.trim() : "",
                status, begin, end,
                filter.usernames(), filter.unrestricted(),
                pageable);
        List<LoginLogVO> records = result.getContent().stream().map(LoginLogVO::from).toList();
        return new PageResult<>(records, result.getTotalElements(), page, size);
    }

    public byte[] exportExcel(String keyword, Integer status, LocalDateTime begin, LocalDateTime end) {
        rbacService.checkPermission("loginlog:export");
        DataScopeService.UsernameFilter filter = dataScopeService.resolveUsernameFilter();
        List<SysLoginLog> rows = loginLogRepository.searchAll(
                StringUtils.hasText(keyword) ? keyword.trim() : "",
                status, begin, end,
                filter.usernames(), filter.unrestricted());
        if (rows.size() > EXPORT_LIMIT) {
            rows = rows.subList(0, EXPORT_LIMIT);
        }
        return ExcelExportUtil.toXlsx(
                "登录日志",
                List.of("ID", "用户名", "IP", "状态", "提示", "浏览器", "登录时间"),
                rows.stream().map(r -> List.of(
                        String.valueOf(r.getId()),
                        nullToEmpty(r.getUsername()),
                        nullToEmpty(r.getIp()),
                        r.getStatus() != null && r.getStatus() == 1 ? "成功" : "失败",
                        nullToEmpty(r.getMessage()),
                        nullToEmpty(r.getUserAgent()),
                        r.getLoginTime() == null ? "" : r.getLoginTime().toString()
                )).toList());
    }

    @Transactional
    public void delete(Long id) {
        rbacService.checkPermission("loginlog:delete");
        SysLoginLog entity = loginLogRepository.findById(id).orElse(null);
        if (entity != null) {
            dataScopeService.assertUsernameAccessible(entity.getUsername());
            loginLogRepository.deleteById(id);
        }
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        rbacService.checkPermission("loginlog:delete");
        int count = 0;
        for (Long id : ids) {
            SysLoginLog entity = loginLogRepository.findById(id).orElse(null);
            if (entity == null) {
                continue;
            }
            dataScopeService.assertUsernameAccessible(entity.getUsername());
            loginLogRepository.delete(entity);
            count++;
        }
        return count;
    }

    @Transactional
    public void clean() {
        rbacService.checkPermission("loginlog:clean");
        DataScopeService.UsernameFilter filter = dataScopeService.resolveUsernameFilter();
        if (filter.unrestricted()) {
            loginLogRepository.deleteAllInBatch();
            return;
        }
        List<SysLoginLog> rows = loginLogRepository.searchAll(
                "", null, null, null, filter.usernames(), false);
        loginLogRepository.deleteAllInBatch(rows);
    }

    @Transactional
    public int deleteBefore(LocalDateTime before) {
        if (before == null) {
            return 0;
        }
        return loginLogRepository.deleteByLoginTimeBefore(before);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
