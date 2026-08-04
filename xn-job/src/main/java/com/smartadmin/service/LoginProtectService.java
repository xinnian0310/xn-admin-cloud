package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 登录失败锁定（账号级）+ IP 限流。策略读自 {@link SecurityPolicyService}（可管理端配置）。
 * 超级管理员（SUPER_ADMIN 角色）不受登录失败锁定。
 */
@Service
@RequiredArgsConstructor
public class LoginProtectService {

    private static final String FAIL_PREFIX = "login:fail:";
    private static final String RATE_PREFIX = "login:rate:";

    private final AppKvStore kvStore;
    private final SecurityPolicyService securityPolicyService;
    private final UserRepository userRepository;

    public void checkBeforeLogin(String username, String ip) {
        var cfg = securityPolicyService.effectiveLogin();
        String userKey = normalizeUser(username);
        if (StringUtils.hasText(userKey) && !isSuperAdminUsername(username)) {
            String lockKey = SecurityPolicyService.LOCK_PREFIX + userKey;
            if (kvStore.exists(lockKey)) {
                Long ttl = kvStore.ttlSeconds(lockKey);
                String tip = ttl != null && ttl > 0
                        ? "账号已锁定，请 " + formatRemain(ttl) + " 后再试"
                        : "账号已锁定，请稍后再试";
                throw new BusinessException(423, tip);
            }
        }

        String ipKey = StringUtils.hasText(ip) ? ip.trim() : "unknown";
        long hits = kvStore.incr(RATE_PREFIX + ipKey, Duration.ofMinutes(1));
        if (hits > cfg.getRateLimitPerMinute()) {
            throw new BusinessException(429, "请求过于频繁，请稍后再试");
        }
    }

    public void onLoginFail(String username) {
        if (isSuperAdminUsername(username)) {
            return;
        }
        var cfg = securityPolicyService.effectiveLogin();
        String userKey = normalizeUser(username);
        if (!StringUtils.hasText(userKey)) {
            return;
        }
        Duration window = Duration.ofMinutes(Math.max(1, cfg.getLockMinutes()));
        long fails = kvStore.incr(FAIL_PREFIX + userKey, window);
        if (fails >= cfg.getMaxFailures()) {
            kvStore.set(SecurityPolicyService.LOCK_PREFIX + userKey, "1", window);
            kvStore.delete(FAIL_PREFIX + userKey);
        }
    }

    public void onLoginSuccess(String username) {
        String userKey = normalizeUser(username);
        if (!StringUtils.hasText(userKey)) {
            return;
        }
        kvStore.delete(FAIL_PREFIX + userKey);
        kvStore.delete(SecurityPolicyService.LOCK_PREFIX + userKey);
    }

    /** 按用户名判断是否超级管理员（登录前无认证上下文） */
    private boolean isSuperAdminUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        return userRepository.findByUsernameWithRolesIgnoreCase(username.trim())
                .map(user -> user.getRoles() != null && user.getRoles().stream()
                        .anyMatch(r -> RbacService.SUPER_ADMIN_CODE.equals(r.getCode())))
                .orElse(false);
    }

    private static String normalizeUser(String username) {
        return StringUtils.hasText(username) ? username.trim().toLowerCase() : "";
    }

    private static String formatRemain(long seconds) {
        if (seconds >= 60) {
            long m = (seconds + 59) / 60;
            return m + " 分钟";
        }
        return seconds + " 秒";
    }
}
