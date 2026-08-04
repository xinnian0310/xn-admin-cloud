package com.smartadmin.service;

import com.smartadmin.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * JWT 黑名单：踢人 / 登出 / 禁用后立即使令牌失效。
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String TOKEN_PREFIX = "jwt:bl:";
    private static final String USER_PREFIX = "jwt:userbl:";

    private final AppKvStore kvStore;

    @Value("${app.jwt.expiration:86400000}")
    private long jwtExpirationMs;

    public void revokeToken(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        String key = TOKEN_PREFIX + sha256(token.trim());
        long ttlSec = Math.max(60, jwtExpirationMs / 1000);
        kvStore.set(key, "1", Duration.ofSeconds(ttlSec));
    }

    /** 按用户撤销：该用户在此时间点之前签发的 token 全部失效（配合 iat 校验）。 */
    public void revokeUser(Long userId) {
        if (userId == null) {
            return;
        }
        long ttlSec = Math.max(60, jwtExpirationMs / 1000);
        kvStore.set(USER_PREFIX + userId, String.valueOf(System.currentTimeMillis()), Duration.ofSeconds(ttlSec));
    }

    public boolean isRevoked(String token, Long userId, long issuedAtMs) {
        if (StringUtils.hasText(token) && kvStore.exists(TOKEN_PREFIX + sha256(token.trim()))) {
            return true;
        }
        if (userId == null) {
            return false;
        }
        String stamp = kvStore.get(USER_PREFIX + userId);
        if (!StringUtils.hasText(stamp)) {
            return false;
        }
        try {
            long revokedBefore = Long.parseLong(stamp);
            return issuedAtMs <= revokedBefore;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(raw.hashCode());
        }
    }
}
