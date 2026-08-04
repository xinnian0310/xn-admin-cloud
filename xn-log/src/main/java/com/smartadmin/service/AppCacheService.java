package com.smartadmin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * 业务缓存：基于 {@link AppKvStore}（Redis 优先，内存回落）。
 * 缓存权限码、字典、公开配置、菜单树等高频只读数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppCacheService {

    public static final String PREFIX_PERM = "cache:perm:";
    public static final String PREFIX_DICT = "cache:dict:";
    public static final String KEY_APP_CONFIG = "cache:app-config:public";
    public static final String PREFIX_MENUS = "cache:menus:";

    private static final Duration TTL_PERM = Duration.ofMinutes(30);
    private static final Duration TTL_DICT = Duration.ofHours(1);
    private static final Duration TTL_APP_CONFIG = Duration.ofMinutes(10);
    private static final Duration TTL_MENUS = Duration.ofMinutes(30);

    private final AppKvStore kvStore;
    private final ObjectMapper objectMapper;

    public <T> T getOrLoad(String key, Duration ttl, TypeReference<T> type, Supplier<T> loader) {
        String cached = kvStore.get(key);
        if (StringUtils.hasText(cached)) {
            try {
                return objectMapper.readValue(cached, type);
            } catch (Exception ex) {
                log.warn("缓存反序列化失败，key={}：{}", key, ex.getMessage());
                kvStore.delete(key);
            }
        }
        T value = loader.get();
        put(key, value, ttl);
        return value;
    }

    public void put(String key, Object value, Duration ttl) {
        if (value == null) {
            return;
        }
        try {
            kvStore.set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ex) {
            log.warn("缓存写入失败，key={}：{}", key, ex.getMessage());
        }
    }

    public void evict(String key) {
        kvStore.delete(key);
    }

    public void evictByPrefix(String prefix) {
        kvStore.deleteByPrefix(prefix);
    }

    public List<String> getPermissionCodes(Long userId, Supplier<List<String>> loader) {
        return getOrLoad(PREFIX_PERM + userId, TTL_PERM, new TypeReference<>() {
        }, loader);
    }

    public void evictPermissionCodes(Long userId) {
        if (userId != null) {
            evict(PREFIX_PERM + userId);
            evict(PREFIX_MENUS + userId);
        }
    }

    public void evictAllPermissionCaches() {
        evictByPrefix(PREFIX_PERM);
        evictByPrefix(PREFIX_MENUS);
    }

    public <T> List<T> getDict(String dictType, TypeReference<List<T>> type, Supplier<List<T>> loader) {
        return getOrLoad(PREFIX_DICT + dictType, TTL_DICT, type, loader);
    }

    public void evictDict(String dictType) {
        if (StringUtils.hasText(dictType)) {
            evict(PREFIX_DICT + dictType);
        }
    }

    public void evictAllDict() {
        evictByPrefix(PREFIX_DICT);
    }

    public <T> T getAppConfig(TypeReference<T> type, Supplier<T> loader) {
        return getOrLoad(KEY_APP_CONFIG, TTL_APP_CONFIG, type, loader);
    }

    public void evictAppConfig() {
        evict(KEY_APP_CONFIG);
    }

    public <T> T getMenus(Long userId, TypeReference<T> type, Supplier<T> loader) {
        return getOrLoad(PREFIX_MENUS + userId, TTL_MENUS, type, loader);
    }
}
