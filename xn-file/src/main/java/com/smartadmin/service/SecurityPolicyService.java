package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.config.SecurityProperties;
import com.smartadmin.dto.LockedAccountVO;
import com.smartadmin.dto.SecurityPolicyVO;
import com.smartadmin.entity.SysSecurityPolicy;
import com.smartadmin.repository.SysSecurityPolicyRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SecurityPolicyService {

    public static final String LOCK_PREFIX = "login:lock:";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SysSecurityPolicyRepository repository;
    private final SecurityProperties securityProperties;
    private final AppKvStore kvStore;
    private final RbacService rbacService;

    @PostConstruct
    public void ensureDefaults() {
        if (repository.findById(1L).isEmpty()) {
            SysSecurityPolicy policy = new SysSecurityPolicy();
            policy.setId(1L);
            SecurityProperties.Login def = securityProperties.getLogin();
            policy.setMaxFailures(def.getMaxFailures());
            policy.setLockMinutes(def.getLockMinutes());
            policy.setRateLimitPerMinute(def.getRateLimitPerMinute());
            policy.setCaptchaTtlSeconds(def.getCaptchaTtlSeconds());
            applyPasswordDefaults(policy);
            repository.save(policy);
        }
    }

    /** 运行时生效的登录策略（供锁定/验证码使用） */
    public SecurityProperties.Login effectiveLogin() {
        SysSecurityPolicy policy = repository.findById(1L).orElse(null);
        SecurityProperties.Login def = securityProperties.getLogin();
        if (policy == null) {
            return def;
        }
        SecurityProperties.Login login = new SecurityProperties.Login();
        login.setMaxFailures(nz(policy.getMaxFailures(), def.getMaxFailures()));
        login.setLockMinutes(nz(policy.getLockMinutes(), def.getLockMinutes()));
        login.setRateLimitPerMinute(nz(policy.getRateLimitPerMinute(), def.getRateLimitPerMinute()));
        login.setCaptchaTtlSeconds(nz(policy.getCaptchaTtlSeconds(), def.getCaptchaTtlSeconds()));
        return login;
    }

    public SecurityPolicyVO get() {
        rbacService.checkPermission("security-policy:refresh");
        return toVo(loadOrCreate());
    }

    @Transactional
    public SecurityPolicyVO update(SecurityPolicyVO request) {
        rbacService.checkPermission("security-policy:update");
        if (request.getPwdMaxLength() < request.getPwdMinLength()) {
            throw new BusinessException("密码最大长度不能小于最小长度");
        }
        SysSecurityPolicy policy = loadOrCreate();
        policy.setMaxFailures(request.getMaxFailures());
        policy.setLockMinutes(request.getLockMinutes());
        policy.setRateLimitPerMinute(request.getRateLimitPerMinute());
        policy.setCaptchaTtlSeconds(request.getCaptchaTtlSeconds());
        policy.setPwdMinLength(request.getPwdMinLength());
        policy.setPwdMaxLength(request.getPwdMaxLength());
        policy.setPwdRequireUpper(request.getPwdRequireUpper());
        policy.setPwdRequireLower(request.getPwdRequireLower());
        policy.setPwdRequireDigit(request.getPwdRequireDigit());
        policy.setPwdRequireSpecial(request.getPwdRequireSpecial());
        policy.setPwdExpireDays(request.getPwdExpireDays());
        policy.setPwdForceChangeFirst(request.getPwdForceChangeFirst());
        policy.setPwdHistoryCount(request.getPwdHistoryCount());
        policy.setUpdatedAt(java.time.LocalDateTime.now());
        return toVo(repository.save(policy));
    }

    public List<LockedAccountVO> listLocked() {
        rbacService.checkPermission("security-policy:refresh");
        List<String> keys = kvStore.keysByPrefix(LOCK_PREFIX);
        List<LockedAccountVO> list = new ArrayList<>();
        for (String key : keys) {
            String username = key.substring(LOCK_PREFIX.length());
            if (!StringUtils.hasText(username)) {
                continue;
            }
            Long ttl = kvStore.ttlSeconds(key);
            if (ttl == null || ttl <= 0) {
                continue;
            }
            list.add(new LockedAccountVO(username, ttl));
        }
        list.sort(Comparator.comparing(LockedAccountVO::getUsername));
        return list;
    }

    public void unlock(String username) {
        rbacService.checkPermission("security-policy:table-unlock");
        String userKey = StringUtils.hasText(username) ? username.trim().toLowerCase() : "";
        if (!StringUtils.hasText(userKey)) {
            throw new BusinessException("请指定用户名");
        }
        kvStore.delete(LOCK_PREFIX + userKey);
        kvStore.delete("login:fail:" + userKey);
    }

    private SysSecurityPolicy loadOrCreate() {
        return repository.findById(1L).orElseGet(() -> {
            ensureDefaults();
            return repository.findById(1L).orElseThrow(() -> new BusinessException("安全策略未初始化"));
        });
    }

    private static void applyPasswordDefaults(SysSecurityPolicy policy) {
        policy.setPwdMinLength(6);
        policy.setPwdMaxLength(50);
        policy.setPwdRequireUpper(false);
        policy.setPwdRequireLower(false);
        policy.setPwdRequireDigit(false);
        policy.setPwdRequireSpecial(false);
        policy.setPwdExpireDays(0);
        policy.setPwdForceChangeFirst(true);
        policy.setPwdHistoryCount(0);
    }

    private static SecurityPolicyVO toVo(SysSecurityPolicy policy) {
        SecurityPolicyVO vo = new SecurityPolicyVO();
        vo.setMaxFailures(policy.getMaxFailures());
        vo.setLockMinutes(policy.getLockMinutes());
        vo.setRateLimitPerMinute(policy.getRateLimitPerMinute());
        vo.setCaptchaTtlSeconds(policy.getCaptchaTtlSeconds());
        vo.setPwdMinLength(nzObj(policy.getPwdMinLength(), 6));
        vo.setPwdMaxLength(nzObj(policy.getPwdMaxLength(), 50));
        vo.setPwdRequireUpper(Boolean.TRUE.equals(policy.getPwdRequireUpper()));
        vo.setPwdRequireLower(Boolean.TRUE.equals(policy.getPwdRequireLower()));
        vo.setPwdRequireDigit(Boolean.TRUE.equals(policy.getPwdRequireDigit()));
        vo.setPwdRequireSpecial(Boolean.TRUE.equals(policy.getPwdRequireSpecial()));
        vo.setPwdExpireDays(nzObj(policy.getPwdExpireDays(), 0));
        vo.setPwdForceChangeFirst(policy.getPwdForceChangeFirst() == null || policy.getPwdForceChangeFirst());
        vo.setPwdHistoryCount(nzObj(policy.getPwdHistoryCount(), 0));
        if (policy.getUpdatedAt() != null) {
            vo.setUpdatedAt(policy.getUpdatedAt().format(FMT));
        }
        return vo;
    }

    private static int nz(Integer value, int fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    private static int nzObj(Integer value, int fallback) {
        return value != null ? value : fallback;
    }
}
