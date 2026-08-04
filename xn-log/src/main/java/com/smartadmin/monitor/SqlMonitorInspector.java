package com.smartadmin.monitor;

import com.smartadmin.dto.SqlRecordVO;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SqlMonitorInspector implements StatementInspector {

    private static final int MAX_RECORDS = 200;

    private final ConcurrentLinkedDeque<SqlRecordVO> records = new ConcurrentLinkedDeque<>();
    private final AtomicLong queryCount = new AtomicLong();
    private final AtomicLong idSeq = new AtomicLong();

    @Override
    public String inspect(String sql) {
        queryCount.incrementAndGet();
        SqlRecordVO record = new SqlRecordVO();
        record.setId(idSeq.incrementAndGet());
        record.setSql(sql);
        record.setDurationMs(null);
        record.setExecutedAt(LocalDateTime.now());
        records.addFirst(record);
        while (records.size() > MAX_RECORDS) {
            records.pollLast();
        }
        return sql;
    }

    public ConcurrentLinkedDeque<SqlRecordVO> getRecords() {
        return records;
    }

    public long getQueryCount() {
        return queryCount.get();
    }

    public void clear() {
        records.clear();
        queryCount.set(0);
    }

    public boolean removeById(Long id) {
        if (id == null) {
            return false;
        }
        return records.removeIf(r -> id.equals(r.getId()));
    }
}
