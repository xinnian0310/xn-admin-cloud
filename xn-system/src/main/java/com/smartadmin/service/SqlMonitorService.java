package com.smartadmin.service;

import com.smartadmin.dto.SqlMonitorVO;
import com.smartadmin.dto.SqlRecordVO;
import com.smartadmin.monitor.SqlMonitorInspector;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SqlMonitorService {

    private final SqlMonitorInspector sqlMonitorInspector;
    private final RbacService rbacService;

    public SqlMonitorVO snapshot() {
        rbacService.checkPermission("api:GET:/api/monitor/sql");
        SqlMonitorVO vo = new SqlMonitorVO();
        vo.setQueryCount(sqlMonitorInspector.getQueryCount());
        vo.setBufferSize(200);
        List<SqlRecordVO> records = new ArrayList<>(sqlMonitorInspector.getRecords());
        vo.setRecords(records);
        return vo;
    }

    public void clean() {
        rbacService.checkPermission("api:DELETE:/api/monitor/sql/clean");
        sqlMonitorInspector.clear();
    }

    public void removeRecord(Long id) {
        rbacService.checkPermission("api:DELETE:/api/monitor/sql/records/{id}");
        if (!sqlMonitorInspector.removeById(id)) {
            throw new com.smartadmin.common.BusinessException("记录不存在或已清除");
        }
    }
}
