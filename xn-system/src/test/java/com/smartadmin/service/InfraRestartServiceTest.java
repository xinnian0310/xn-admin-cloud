package com.smartadmin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartadmin.common.BusinessException;
import com.smartadmin.config.InfraProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 基础设施重启：必须先过权限校验，且默认关闭开关。 */
@ExtendWith(MockitoExtension.class)
class InfraRestartServiceTest {

    private static final String RESTART_PERMISSION = "api:POST:/api/monitor/infra/{name}/restart";

    @Mock private InfraProperties infraProperties;
    @Mock private RbacService rbacService;

    private InfraRestartService service;

    @BeforeEach
    void setUp() {
        service = new InfraRestartService(infraProperties, rbacService);
    }

    @Test
    void restartDeniedWhenNoPermission() {
        doThrow(new BusinessException(403, "无权限"))
                .when(rbacService)
                .checkPermission(RESTART_PERMISSION);

        BusinessException ex =
                assertThrows(BusinessException.class, () -> service.restart("redis"));
        assertEquals(403, ex.getCode());
        verify(infraProperties, never()).isRestartEnabled();
    }

    @Test
    void restartDeniedWhenFeatureDisabled() {
        doNothing().when(rbacService).checkPermission(RESTART_PERMISSION);
        when(infraProperties.isRestartEnabled()).thenReturn(false);

        BusinessException ex =
                assertThrows(BusinessException.class, () -> service.restart("redis"));
        assertTrue(ex.getMessage().contains("restart-enabled=false"));
    }

    @Test
    void restartRejectsUnknownComponent() {
        doNothing().when(rbacService).checkPermission(RESTART_PERMISSION);
        when(infraProperties.isRestartEnabled()).thenReturn(true);

        BusinessException ex =
                assertThrows(BusinessException.class, () -> service.restart("mysql"));
        assertTrue(ex.getMessage().contains("不支持重启组件"));
    }
}
