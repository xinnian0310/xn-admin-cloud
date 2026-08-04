package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.config.RedisProperties;
import com.smartadmin.dto.RedisMonitorVO;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisMonitorService {

    private static final int SAMPLE_KEY_LIMIT = 100;

    private final RedisProperties redisProperties;
    private final RbacService rbacService;

    public RedisMonitorVO info() {
        rbacService.checkPermission("api:GET:/api/monitor/redis");
        RedisMonitorVO vo = new RedisMonitorVO();
        vo.setHost(redisProperties.getHost());
        vo.setPort(redisProperties.getPort());

        if (!redisProperties.isEnabled()) {
            vo.setStatus("DISABLED");
            vo.setMessage("Redis 监控未启用，可在 application.yml 中设置 app.redis.enabled=true");
            return vo;
        }

        RedisClient client = createClient();
        try (StatefulRedisConnection<String, String> connection = client.connect()) {
            RedisCommands<String, String> commands = connection.sync();
            String info = commands.info();
            vo.setStatus("ENABLED");
            vo.setInfo(parseInfo(info));
            Long dbSize = commands.dbsize();
            vo.setKeyCount(dbSize);
            vo.setSampleKeys(sampleKeys(commands));
            return vo;
        } catch (Exception ex) {
            log.warn("Redis 连接失败：{}", ex.getMessage());
            vo.setStatus("ERROR");
            vo.setMessage(ex.getMessage());
            return vo;
        } finally {
            client.shutdown();
        }
    }

    public void deleteKey(String key) {
        rbacService.checkPermission("api:DELETE:/api/monitor/redis/keys");
        if (!redisProperties.isEnabled()) {
            throw new BusinessException("Redis 未启用");
        }
        if (!StringUtils.hasText(key)) {
            throw new BusinessException("请指定 key");
        }
        RedisClient client = createClient();
        try (StatefulRedisConnection<String, String> connection = client.connect()) {
            connection.sync().del(key.trim());
        } catch (Exception ex) {
            throw new BusinessException("删除 key 失败：" + ex.getMessage());
        } finally {
            client.shutdown();
        }
    }

    public void flushDb() {
        rbacService.checkPermission("api:DELETE:/api/monitor/redis/flush");
        if (!redisProperties.isEnabled()) {
            throw new BusinessException("Redis 未启用");
        }
        RedisClient client = createClient();
        try (StatefulRedisConnection<String, String> connection = client.connect()) {
            connection.sync().flushdb();
        } catch (Exception ex) {
            throw new BusinessException("清空数据库失败：" + ex.getMessage());
        } finally {
            client.shutdown();
        }
    }

    private RedisClient createClient() {
        RedisURI.Builder builder =
                RedisURI.builder()
                        .withHost(redisProperties.getHost())
                        .withPort(redisProperties.getPort())
                        .withDatabase(redisProperties.getDatabase());
        if (StringUtils.hasText(redisProperties.getPassword())) {
            builder.withPassword(redisProperties.getPassword().toCharArray());
        }
        return RedisClient.create(builder.build());
    }

    private Map<String, String> parseInfo(String info) {
        Map<String, String> map = new LinkedHashMap<>();
        if (!StringUtils.hasText(info)) {
            return map;
        }
        Properties props = new Properties();
        try {
            props.load(new java.io.StringReader(info.replace(':', '=')));
        } catch (Exception ignored) {
            for (String line : info.split("\r?\n")) {
                if (line.startsWith("#") || !line.contains(":")) {
                    continue;
                }
                int idx = line.indexOf(':');
                map.put(line.substring(0, idx), line.substring(idx + 1));
            }
            return map;
        }
        for (String name : props.stringPropertyNames()) {
            map.put(name, props.getProperty(name));
        }
        return map;
    }

    private List<String> sampleKeys(RedisCommands<String, String> commands) {
        List<String> keys = new ArrayList<>();
        io.lettuce.core.KeyScanCursor<String> cursor =
                commands.scan(io.lettuce.core.ScanArgs.Builder.limit(SAMPLE_KEY_LIMIT));
        while (keys.size() < SAMPLE_KEY_LIMIT) {
            keys.addAll(cursor.getKeys());
            if (!cursor.isFinished() && keys.size() < SAMPLE_KEY_LIMIT) {
                cursor =
                        commands.scan(
                                cursor,
                                io.lettuce.core.ScanArgs.Builder.limit(
                                        SAMPLE_KEY_LIMIT - keys.size()));
            } else {
                break;
            }
        }
        if (keys.size() > SAMPLE_KEY_LIMIT) {
            return keys.subList(0, SAMPLE_KEY_LIMIT);
        }
        return keys;
    }
}
