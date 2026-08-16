package com.smartadmin.security;

import com.smartadmin.dto.ApiRegistryVO;
import com.smartadmin.dto.ApiSignatureVO;
import com.smartadmin.entity.Permission;
import com.smartadmin.entity.PermissionType;
import com.smartadmin.repository.PermissionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 接口权限注册表：以「权限内容」里 API 类型权限为准， 提供接口是否已登记的匹配能力，并缓存结果。 */
@Component
@RequiredArgsConstructor
public class ApiPermissionRegistry {

    private static final Pattern PATH_VAR = Pattern.compile("\\{[^/}]+}");

    private final PermissionRepository permissionRepository;

    /**
     * 缓存存活秒数，超时后下次匹配自动重读库。 微服务下各服务独享进程内缓存，本服务的 {@link #reload()} 无法通知同库的其他服务： 例如在 xn-system
     * 改权限或启动时补齐新接口，xn-file 若不过期就要重启才认。 置 0 或负数则只在启动与本进程改权限时刷新。
     */
    @Value("${app.api-guard.registry-refresh-seconds:30}")
    private long refreshSeconds;

    private volatile List<Entry> apiEntries = List.of();
    private volatile List<String> allCodes = List.of();
    private volatile boolean loaded = false;
    private volatile long loadedAtNanos;

    private record Entry(String code, String method, String rawPath, Pattern pattern) {}

    public synchronized void reload() {
        List<Entry> entries = new ArrayList<>();
        List<String> codes = new ArrayList<>();
        for (Permission p : permissionRepository.findAll()) {
            if (StringUtils.hasText(p.getCode())) {
                codes.add(p.getCode());
            }
            if (p.getType() == PermissionType.API
                    && StringUtils.hasText(p.getPath())
                    && StringUtils.hasText(p.getMethod())) {
                entries.add(
                        new Entry(
                                p.getCode(),
                                p.getMethod().toUpperCase(),
                                p.getPath(),
                                compile(p.getPath())));
            }
        }
        this.apiEntries = entries;
        this.allCodes = codes;
        // 时间戳先于 loaded 写入，读到 loaded=true 的线程必然读到新时间戳
        this.loadedAtNanos = System.nanoTime();
        this.loaded = true;
    }

    private void ensureLoaded() {
        if (isFresh()) {
            return;
        }
        synchronized (this) {
            if (isFresh()) {
                return;
            }
            reload();
        }
    }

    private boolean isFresh() {
        if (!loaded) {
            return false;
        }
        if (refreshSeconds <= 0) {
            return true;
        }
        return System.nanoTime() - loadedAtNanos < TimeUnit.SECONDS.toNanos(refreshSeconds);
    }

    /** 将 /api/users/{id} 编译成正则 ^/api/users/[^/]+$ */
    private Pattern compile(String path) {
        StringBuilder sb = new StringBuilder("^");
        Matcher matcher = PATH_VAR.matcher(path);
        int last = 0;
        while (matcher.find()) {
            sb.append(Pattern.quote(path.substring(last, matcher.start())));
            sb.append("[^/]+");
            last = matcher.end();
        }
        sb.append(Pattern.quote(path.substring(last)));
        sb.append("$");
        return Pattern.compile(sb.toString());
    }

    public boolean isRegistered(String method, String uri) {
        return findCode(method, uri).isPresent();
    }

    /** 匹配已登记接口对应的权限码，如 api:GET:/api/users */
    public Optional<String> findCode(String method, String uri) {
        ensureLoaded();
        String m = method == null ? "" : method.toUpperCase();
        for (Entry entry : apiEntries) {
            if (entry.method.equals(m) && entry.pattern.matcher(uri).matches()) {
                return Optional.ofNullable(entry.code);
            }
        }
        return Optional.empty();
    }

    public ApiRegistryVO snapshot() {
        ensureLoaded();
        List<ApiSignatureVO> apis =
                apiEntries.stream().map(e -> new ApiSignatureVO(e.method, e.rawPath)).toList();
        return new ApiRegistryVO(apis, List.copyOf(allCodes));
    }
}
