package com.smartadmin.job;

import com.smartadmin.dto.AppConfigVO;
import com.smartadmin.service.AppConfigService;
import com.smartadmin.service.ExceptionLogService;
import com.smartadmin.service.JobLogService;
import com.smartadmin.service.LoginLogService;
import com.smartadmin.service.OperLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 按系统配置的保留天数清理过期日志（登录 / 操作 / 异常 / 任务）。
 */
@Slf4j
@Component("logRetentionJob")
@RequiredArgsConstructor
public class LogRetentionJob {

    private final AppConfigService appConfigService;
    private final LoginLogService loginLogService;
    private final OperLogService operLogService;
    private final ExceptionLogService exceptionLogService;
    private final JobLogService jobLogService;

    @Transactional
    public void cleanExpired() {
        AppConfigVO.LogRetentionConfig cfg = appConfigService.getPublic().getLogRetention();
        if (cfg == null) {
            cfg = new AppConfigVO.LogRetentionConfig();
        }
        LocalDateTime now = LocalDateTime.now();
        int loginDeleted = cleanOne("login", cfg.getLoginDays(),
                days -> loginLogService.deleteBefore(now.minusDays(days)));
        int operDeleted = cleanOne("oper", cfg.getOperDays(),
                days -> operLogService.deleteBefore(now.minusDays(days)));
        int exDeleted = cleanOne("exception", cfg.getExceptionDays(),
                days -> exceptionLogService.deleteBefore(now.minusDays(days)));
        int jobDeleted = cleanOne("job", cfg.getJobDays(),
                days -> jobLogService.deleteBefore(now.minusDays(days)));
        log.info("[LogRetentionJob] cleaned login={}, oper={}, exception={}, job={}",
                loginDeleted, operDeleted, exDeleted, jobDeleted);
    }

    private int cleanOne(String name, Integer days, java.util.function.IntFunction<Integer> cleaner) {
        if (days == null || days <= 0) {
            log.debug("[LogRetentionJob] skip {} (days={})", name, days);
            return 0;
        }
        return cleaner.apply(days);
    }
}
