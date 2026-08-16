package com.smartadmin.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartadmin.entity.Permission;
import com.smartadmin.entity.PermissionType;
import com.smartadmin.repository.PermissionRepository;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** 接口注册表缓存：TTL 到期后重读库，使同库其他服务的权限变更无需重启即可生效。 */
@ExtendWith(MockitoExtension.class)
class ApiPermissionRegistryTest {

    @Mock private PermissionRepository permissionRepository;

    private Permission api(String path) {
        Permission p = new Permission();
        p.setCode("api:GET:" + path);
        p.setType(PermissionType.API);
        p.setMethod("GET");
        p.setPath(path);
        return p;
    }

    private ApiPermissionRegistry registry(long refreshSeconds) {
        ApiPermissionRegistry registry = new ApiPermissionRegistry(permissionRepository);
        ReflectionTestUtils.setField(registry, "refreshSeconds", refreshSeconds);
        return registry;
    }

    /** 首次匹配触发懒加载，路径变量按段通配 */
    @Test
    void matchesRegisteredPathWithVariable() {
        when(permissionRepository.findAll())
                .thenReturn(List.of(api("/api/files/chunk/{id}/status")));
        ApiPermissionRegistry registry = registry(30);

        assertTrue(registry.isRegistered("GET", "/api/files/chunk/abc-123/status"));
        assertFalse(registry.isRegistered("POST", "/api/files/chunk/abc-123/status"));
        assertFalse(registry.isRegistered("GET", "/api/files/chunk/abc/123/status"));
    }

    /** TTL 未到期只读一次库，避免每个请求都全表扫描 */
    @Test
    void cachesWithinTtl() {
        when(permissionRepository.findAll()).thenReturn(List.of(api("/api/files")));
        ApiPermissionRegistry registry = registry(30);

        assertTrue(registry.isRegistered("GET", "/api/files"));
        assertTrue(registry.isRegistered("GET", "/api/files"));

        verify(permissionRepository, times(1)).findAll();
    }

    /** TTL 到期后重读，认出其他服务刚登记的新接口 */
    @Test
    void reloadsAfterTtlExpires() {
        when(permissionRepository.findAll())
                .thenReturn(List.of(api("/api/files")))
                .thenReturn(List.of(api("/api/files"), api("/api/files/chunk/check")));
        ApiPermissionRegistry registry = registry(30);

        assertFalse(registry.isRegistered("GET", "/api/files/chunk/check"));
        expire(registry, 31);

        assertTrue(registry.isRegistered("GET", "/api/files/chunk/check"));
        verify(permissionRepository, times(2)).findAll();
    }

    /** 置 0 关闭自动刷新：仍保持原有的进程内永久缓存行为 */
    @Test
    void neverExpiresWhenRefreshDisabled() {
        when(permissionRepository.findAll()).thenReturn(List.of(api("/api/files")));
        ApiPermissionRegistry registry = registry(0);

        assertTrue(registry.isRegistered("GET", "/api/files"));
        expire(registry, 3600);

        assertTrue(registry.isRegistered("GET", "/api/files"));
        verify(permissionRepository, times(1)).findAll();
    }

    /** 把加载时间戳回拨，模拟缓存过期 */
    private void expire(ApiPermissionRegistry registry, long secondsAgo) {
        long loadedAt = (long) ReflectionTestUtils.getField(registry, "loadedAtNanos");
        ReflectionTestUtils.setField(
                registry, "loadedAtNanos", loadedAt - TimeUnit.SECONDS.toNanos(secondsAgo));
    }
}
