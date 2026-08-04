package com.smartadmin.service;

import com.smartadmin.config.RedisProperties;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 轻量 KV：优先 Redis，不可用时回落到带过期时间的内存 Map。 用于验证码、登录锁定、限流等短生命周期数据。 */
@Slf4j
@Service
public class AppKvStore {

    private static final Duration RECONNECT_BACKOFF = Duration.ofSeconds(30);

    private final RedisProperties redisProperties;
    private final Map<String, Entry> local = new ConcurrentHashMap<>();

    private volatile RedisClient redisClient;
    private volatile StatefulRedisConnection<String, String> connection;
    private volatile boolean redisReady;
    private volatile Instant nextReconnectAt = Instant.EPOCH;

    public AppKvStore(RedisProperties redisProperties) {
        this.redisProperties = redisProperties;
        reconnectIfNeeded(false);
    }

    public void set(String key, String value, Duration ttl) {
        Boolean ok =
                tryRedis(
                        cmds -> {
                            cmds.set(key, value, SetArgs.Builder.ex(Math.max(1, ttl.getSeconds())));
                            return Boolean.TRUE;
                        });
        if (Boolean.TRUE.equals(ok)) {
            return;
        }
        local.put(key, new Entry(value, Instant.now().plus(ttl)));
    }

    public String get(String key) {
        String remote = tryRedis(cmds -> cmds.get(key));
        if (redisReady) {
            return remote;
        }
        Entry entry = local.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt.isBefore(Instant.now())) {
            local.remove(key);
            return null;
        }
        return entry.value;
    }

    public void delete(String key) {
        tryRedis(
                cmds -> {
                    cmds.del(key);
                    return true;
                });
        local.remove(key);
    }

    /** 按前缀删除（Redis 用 KEYS+DEL；内存遍历）。 */
    public void deleteByPrefix(String prefix) {
        List<String> keys = keysByPrefix(prefix);
        for (String key : keys) {
            delete(key);
        }
    }

    /** 自增；key 不存在时以 1 起步并设置 TTL（仅首次）。 */
    public long incr(String key, Duration ttlIfNew) {
        Long remote =
                tryRedis(
                        cmds -> {
                            Long n = cmds.incr(key);
                            if (n != null && n == 1L) {
                                cmds.expire(key, Math.max(1, ttlIfNew.getSeconds()));
                            }
                            return n;
                        });
        if (remote != null && redisReady) {
            return remote;
        }
        Entry existing = local.get(key);
        if (existing == null || existing.expireAt.isBefore(Instant.now())) {
            local.put(key, new Entry("1", Instant.now().plus(ttlIfNew)));
            return 1L;
        }
        long next = Long.parseLong(existing.value) + 1;
        local.put(key, new Entry(String.valueOf(next), existing.expireAt));
        return next;
    }

    public Long ttlSeconds(String key) {
        Long remote = tryRedis(cmds -> cmds.ttl(key));
        if (remote != null && redisReady) {
            return remote < 0 ? null : remote;
        }
        Entry entry = local.get(key);
        if (entry == null) {
            return null;
        }
        long sec = Duration.between(Instant.now(), entry.expireAt).getSeconds();
        return sec <= 0 ? null : sec;
    }

    public boolean exists(String key) {
        return get(key) != null;
    }

    /** 按前缀列出未过期 key（Redis 用 KEYS；内存遍历）。仅供管理端列举锁定账号等场景。 */
    public List<String> keysByPrefix(String prefix) {
        List<String> remote =
                tryRedis(
                        cmds -> {
                            List<String> keys = cmds.keys(prefix + "*");
                            return keys != null ? keys : List.<String>of();
                        });
        if (remote != null && redisReady) {
            return remote;
        }
        Instant now = Instant.now();
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Entry> e : local.entrySet()) {
            if (e.getKey().startsWith(prefix) && e.getValue().expireAt.isAfter(now)) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    private <T> T tryRedis(RedisOp<T> op) {
        reconnectIfNeeded(false);
        if (!redisReady || connection == null) {
            return null;
        }
        try {
            return op.apply(connection.sync());
        } catch (Exception ex) {
            log.warn("Redis KV 操作失败，回落内存：{}", ex.getMessage());
            markRedisDown();
            return null;
        }
    }

    private synchronized void reconnectIfNeeded(boolean force) {
        if (!redisProperties.isEnabled()) {
            redisReady = false;
            return;
        }
        if (redisReady && connection != null && connection.isOpen()) {
            return;
        }
        if (!force && Instant.now().isBefore(nextReconnectAt)) {
            return;
        }
        closeRedisQuietly();
        try {
            RedisURI.Builder builder =
                    RedisURI.builder()
                            .withHost(redisProperties.getHost())
                            .withPort(redisProperties.getPort())
                            .withDatabase(redisProperties.getDatabase())
                            .withTimeout(Duration.ofMillis(500));
            if (StringUtils.hasText(redisProperties.getPassword())) {
                builder.withPassword(redisProperties.getPassword().toCharArray());
            }
            redisClient = RedisClient.create(builder.build());
            connection = redisClient.connect();
            connection.sync().ping();
            redisReady = true;
            nextReconnectAt = Instant.EPOCH;
            log.info(
                    "AppKvStore 已连接 Redis {}:{}",
                    redisProperties.getHost(),
                    redisProperties.getPort());
        } catch (Exception ex) {
            log.warn(
                    "AppKvStore 无法连接 Redis，使用内存存储（{}s 后重试）：{}",
                    RECONNECT_BACKOFF.getSeconds(),
                    ex.getMessage());
            markRedisDown();
        }
    }

    private void markRedisDown() {
        redisReady = false;
        nextReconnectAt = Instant.now().plus(RECONNECT_BACKOFF);
        closeRedisQuietly();
    }

    private void closeRedisQuietly() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (redisClient != null) {
                redisClient.shutdown();
            }
        } catch (Exception ignored) {
        }
        connection = null;
        redisClient = null;
    }

    @PreDestroy
    public void destroy() {
        closeRedisQuietly();
        local.clear();
    }

    @FunctionalInterface
    private interface RedisOp<T> {
        T apply(RedisCommands<String, String> cmds);
    }

    private record Entry(String value, Instant expireAt) {}
}
