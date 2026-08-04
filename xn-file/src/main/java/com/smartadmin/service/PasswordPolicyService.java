package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.PasswordRulesVO;
import com.smartadmin.entity.SysSecurityPolicy;
import com.smartadmin.entity.SysUserPasswordHistory;
import com.smartadmin.entity.User;
import com.smartadmin.repository.SysSecurityPolicyRepository;
import com.smartadmin.repository.SysUserPasswordHistoryRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 密码策略：复杂度校验、历史复用、过期/强制改密判定、统一写密。 */
@Service
@RequiredArgsConstructor
public class PasswordPolicyService {

    private final SysSecurityPolicyRepository securityPolicyRepository;
    private final SysUserPasswordHistoryRepository historyRepository;
    private final PasswordEncoder passwordEncoder;
    private final RbacService rbacService;

    public SysSecurityPolicy loadPolicy() {
        return securityPolicyRepository.findById(1L).orElseGet(SysSecurityPolicy::new);
    }

    public PasswordRulesVO rules() {
        return toRules(loadPolicy());
    }

    public void validateComplexity(String rawPassword) {
        SysSecurityPolicy policy = loadPolicy();
        validateComplexity(rawPassword, policy);
    }

    public void validateComplexity(String rawPassword, SysSecurityPolicy policy) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new BusinessException("密码不能为空");
        }
        int min = nz(policy.getPwdMinLength(), 6);
        int max = nz(policy.getPwdMaxLength(), 50);
        if (max < min) {
            max = min;
        }
        int len = rawPassword.length();
        if (len < min || len > max) {
            throw new BusinessException("密码长度需在" + min + "-" + max + "之间");
        }
        if (Boolean.TRUE.equals(policy.getPwdRequireUpper()) && !rawPassword.matches(".*[A-Z].*")) {
            throw new BusinessException("密码须包含大写字母");
        }
        if (Boolean.TRUE.equals(policy.getPwdRequireLower()) && !rawPassword.matches(".*[a-z].*")) {
            throw new BusinessException("密码须包含小写字母");
        }
        if (Boolean.TRUE.equals(policy.getPwdRequireDigit()) && !rawPassword.matches(".*\\d.*")) {
            throw new BusinessException("密码须包含数字");
        }
        if (Boolean.TRUE.equals(policy.getPwdRequireSpecial())
                && !rawPassword.matches(".*[^A-Za-z0-9].*")) {
            throw new BusinessException("密码须包含特殊字符");
        }
    }

    /**
     * 校验新密码：复杂度 + 不可与当前相同 + 不可复用历史。
     *
     * @param currentEncodedPassword 用户当前密文，可空（新建用户）
     */
    public void validateNewPassword(
            String rawPassword, Long userId, String currentEncodedPassword) {
        SysSecurityPolicy policy = loadPolicy();
        validateComplexity(rawPassword, policy);

        if (StringUtils.hasText(currentEncodedPassword)
                && passwordEncoder.matches(rawPassword, currentEncodedPassword)) {
            throw new BusinessException("新密码不能与原密码相同");
        }

        int historyCount = nz(policy.getPwdHistoryCount(), 0);
        if (historyCount <= 0 || userId == null) {
            return;
        }
        List<SysUserPasswordHistory> histories =
                historyRepository.findByUserIdOrderByCreatedAtDesc(userId);
        int checked = 0;
        for (SysUserPasswordHistory item : histories) {
            if (checked >= historyCount) {
                break;
            }
            if (passwordEncoder.matches(rawPassword, item.getPasswordHash())) {
                throw new BusinessException("新密码不能与最近使用过的密码相同");
            }
            checked++;
        }
    }

    /**
     * 写入新密码：编码、记录历史、更新改密时间；管理员重置时按策略置强制改密。
     *
     * @param adminReset true=管理员新建/重置；false=用户自助改密（清除强制改密）
     */
    @Transactional
    public void assignPassword(User user, String rawPassword, boolean adminReset) {
        validateNewPassword(rawPassword, user.getId(), user.getPassword());

        if (user.getId() != null && StringUtils.hasText(user.getPassword())) {
            pushHistory(user.getId(), user.getPassword());
        }

        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setPasswordChangedAt(LocalDateTime.now());

        SysSecurityPolicy policy = loadPolicy();
        if (adminReset && Boolean.TRUE.equals(policy.getPwdForceChangeFirst())) {
            user.setPwdForceChange(true);
        } else {
            user.setPwdForceChange(false);
        }

        trimHistory(user.getId(), nz(policy.getPwdHistoryCount(), 0));
    }

    /** 是否需强制改密（超管豁免） */
    public boolean mustChangePassword(User user) {
        if (user == null || rbacService.isSuperAdmin(user)) {
            return false;
        }
        if (Boolean.TRUE.equals(user.getPwdForceChange())) {
            return true;
        }
        return isPasswordExpired(user);
    }

    public boolean isPasswordExpired(User user) {
        if (user == null || rbacService.isSuperAdmin(user)) {
            return false;
        }
        SysSecurityPolicy policy = loadPolicy();
        int expireDays = nz(policy.getPwdExpireDays(), 0);
        if (expireDays <= 0) {
            return false;
        }
        LocalDateTime changedAt = user.getPasswordChangedAt();
        if (changedAt == null) {
            // 历史账号未记录改密时间：不过期，避免一上线全员强制改密
            return false;
        }
        return changedAt.plusDays(expireDays).isBefore(LocalDateTime.now());
    }

    public PasswordRulesVO toRules(SysSecurityPolicy policy) {
        PasswordRulesVO vo = new PasswordRulesVO();
        int min = nz(policy.getPwdMinLength(), 6);
        int max = nz(policy.getPwdMaxLength(), 50);
        if (max < min) {
            max = min;
        }
        vo.setMinLength(min);
        vo.setMaxLength(max);
        vo.setRequireUpper(Boolean.TRUE.equals(policy.getPwdRequireUpper()));
        vo.setRequireLower(Boolean.TRUE.equals(policy.getPwdRequireLower()));
        vo.setRequireDigit(Boolean.TRUE.equals(policy.getPwdRequireDigit()));
        vo.setRequireSpecial(Boolean.TRUE.equals(policy.getPwdRequireSpecial()));
        vo.setExpireDays(nz(policy.getPwdExpireDays(), 0));
        vo.setForceChangeFirst(Boolean.TRUE.equals(policy.getPwdForceChangeFirst()));
        vo.setHistoryCount(nz(policy.getPwdHistoryCount(), 0));
        vo.setTip(buildTip(vo));
        return vo;
    }

    private void pushHistory(Long userId, String passwordHash) {
        SysUserPasswordHistory row = new SysUserPasswordHistory();
        row.setUserId(userId);
        row.setPasswordHash(passwordHash);
        historyRepository.save(row);
    }

    private void trimHistory(Long userId, int keep) {
        if (userId == null) {
            return;
        }
        List<SysUserPasswordHistory> histories =
                historyRepository.findByUserIdOrderByCreatedAtDesc(userId);
        // keep 表示「禁止复用最近 N 次」；库内多留一点冗余无妨，超出 keep+5 再删
        int limit = Math.max(keep, 0) + 5;
        if (histories.size() <= limit) {
            return;
        }
        List<SysUserPasswordHistory> remove = histories.subList(limit, histories.size());
        historyRepository.deleteAll(remove);
    }

    private static String buildTip(PasswordRulesVO rules) {
        List<String> parts = new ArrayList<>();
        parts.add(rules.getMinLength() + "-" + rules.getMaxLength() + " 位");
        if (Boolean.TRUE.equals(rules.getRequireUpper())) {
            parts.add("含大写字母");
        }
        if (Boolean.TRUE.equals(rules.getRequireLower())) {
            parts.add("含小写字母");
        }
        if (Boolean.TRUE.equals(rules.getRequireDigit())) {
            parts.add("含数字");
        }
        if (Boolean.TRUE.equals(rules.getRequireSpecial())) {
            parts.add("含特殊字符");
        }
        if (rules.getHistoryCount() != null && rules.getHistoryCount() > 0) {
            parts.add("不可与近 " + rules.getHistoryCount() + " 次相同");
        }
        if (rules.getExpireDays() != null && rules.getExpireDays() > 0) {
            parts.add("每 " + rules.getExpireDays() + " 天需更换");
        }
        return String.join("，", parts);
    }

    private static int nz(Integer value, int fallback) {
        return value != null ? value : fallback;
    }
}
