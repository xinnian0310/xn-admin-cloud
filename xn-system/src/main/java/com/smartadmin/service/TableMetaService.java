package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.ColumnMetaVO;
import com.smartadmin.dto.TableInfoVO;
import com.smartadmin.util.CodegenNaming;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TableMetaService {

    private final DataSource dataSource;
    private final RbacService rbacService;

    public List<TableInfoVO> listTables(boolean includeSys) {
        rbacService.checkPermission("codegen:generate");
        List<TableInfoVO> list = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            try (ResultSet rs = meta.getTables(catalog, null, "%", new String[] {"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (!StringUtils.hasText(name)) continue;
                    if (!includeSys && name.toLowerCase(Locale.ROOT).startsWith("sys_")) {
                        continue;
                    }
                    // flyway / 内部表
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (lower.equals("flyway_schema_history") || lower.startsWith("qrtz_")) {
                        continue;
                    }
                    TableInfoVO vo = new TableInfoVO();
                    vo.setTableName(name);
                    vo.setRemarks(rs.getString("REMARKS"));
                    list.add(vo);
                }
            }
        } catch (SQLException e) {
            throw new BusinessException("读取数据表失败: " + e.getMessage());
        }
        list.sort((a, b) -> a.getTableName().compareToIgnoreCase(b.getTableName()));
        return list;
    }

    public List<ColumnMetaVO> listColumns(String tableName) {
        rbacService.checkPermission("codegen:generate");
        if (!StringUtils.hasText(tableName)) {
            throw new BusinessException("表名不能为空");
        }
        String table = tableName.trim();
        Set<String> pks = new HashSet<>();
        List<ColumnMetaVO> columns = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            try (ResultSet pkRs = meta.getPrimaryKeys(catalog, null, table)) {
                while (pkRs.next()) {
                    pks.add(pkRs.getString("COLUMN_NAME"));
                }
            }
            // MySQL 表名大小写：再试一次小写
            if (pks.isEmpty()) {
                try (ResultSet pkRs =
                        meta.getPrimaryKeys(catalog, null, table.toLowerCase(Locale.ROOT))) {
                    while (pkRs.next()) {
                        pks.add(pkRs.getString("COLUMN_NAME"));
                    }
                }
            }
            try (ResultSet rs = meta.getColumns(catalog, null, table, "%")) {
                while (rs.next()) {
                    columns.add(mapColumn(rs, pks));
                }
            }
            if (columns.isEmpty()) {
                try (ResultSet rs =
                        meta.getColumns(catalog, null, table.toLowerCase(Locale.ROOT), "%")) {
                    while (rs.next()) {
                        columns.add(mapColumn(rs, pks));
                    }
                }
            }
        } catch (SQLException e) {
            throw new BusinessException("读取表字段失败: " + e.getMessage());
        }
        if (columns.isEmpty()) {
            throw new BusinessException("未找到表或字段: " + table);
        }
        return columns;
    }

    private ColumnMetaVO mapColumn(ResultSet rs, Set<String> pks) throws SQLException {
        String columnName = rs.getString("COLUMN_NAME");
        String typeName = rs.getString("TYPE_NAME");
        int dataType = rs.getInt("DATA_TYPE");
        int size = rs.getInt("COLUMN_SIZE");
        int nullable = rs.getInt("NULLABLE");
        String remarks = rs.getString("REMARKS");
        boolean pk = pks.stream().anyMatch(p -> p.equalsIgnoreCase(columnName));

        ColumnMetaVO vo = new ColumnMetaVO();
        vo.setColumnName(columnName);
        vo.setRemarks(remarks);
        vo.setLabel(StringUtils.hasText(remarks) ? remarks : columnName);
        vo.setJdbcType(typeName);
        vo.setColumnSize(size > 0 ? size : null);
        vo.setPk(pk);
        vo.setNullable(nullable != DatabaseMetaData.columnNoNulls);
        vo.setJavaField(CodegenNaming.columnToCamel(columnName));

        TypeMapping mapping = mapJavaType(columnName, typeName, dataType, size);
        vo.setJavaType(mapping.javaType);
        vo.setFormType(mapping.formType);

        String lower = columnName.toLowerCase(Locale.ROOT);
        boolean audit =
                lower.equals("created_at")
                        || lower.equals("updated_at")
                        || lower.equals("create_time")
                        || lower.equals("update_time");
        boolean builtin = lower.equals("built_in") || lower.equals("builtin");

        vo.setListShow(!pk && !builtin);
        vo.setQueryable(
                !pk
                        && !audit
                        && !builtin
                        && ("String".equals(mapping.javaType)
                                || lower.contains("name")
                                || lower.contains("code")
                                || lower.equals("status")));
        vo.setFormShow(!pk && !audit && !builtin);
        vo.setRequired(!vo.isNullable() && vo.isFormShow());

        if (pk) {
            vo.setListShow(true);
            vo.setFormShow(false);
            vo.setQueryable(false);
            vo.setRequired(false);
        }
        return vo;
    }

    private TypeMapping mapJavaType(String columnName, String typeName, int dataType, int size) {
        String type = typeName == null ? "" : typeName.toUpperCase(Locale.ROOT);
        String lowerCol = columnName.toLowerCase(Locale.ROOT);

        if ("status".equals(lowerCol)
                && (type.contains("INT") || type.contains("TINYINT") || type.contains("BIT"))) {
            return new TypeMapping("Integer", "select");
        }
        if (type.contains("BIGINT")) {
            return new TypeMapping("Long", "number");
        }
        if (type.contains("INT") || type.contains("SMALLINT") || type.contains("TINYINT")) {
            if ((type.contains("TINYINT") || type.contains("BIT"))
                    && size <= 1
                    && !("status".equals(lowerCol))) {
                return new TypeMapping("Boolean", "select");
            }
            return new TypeMapping("Integer", "number");
        }
        if (type.contains("DECIMAL")
                || type.contains("NUMERIC")
                || type.contains("DOUBLE")
                || type.contains("FLOAT")
                || type.contains("REAL")) {
            return new TypeMapping("BigDecimal", "number");
        }
        if (type.contains("DATETIME") || type.contains("TIMESTAMP")) {
            return new TypeMapping("LocalDateTime", "datetime");
        }
        if (type.equals("DATE")) {
            return new TypeMapping("LocalDate", "datetime");
        }
        if (type.contains("TEXT") || type.contains("BLOB") || type.contains("CLOB") || size > 200) {
            return new TypeMapping("String", "textarea");
        }
        if (dataType == java.sql.Types.BOOLEAN || type.contains("BIT") || type.contains("BOOL")) {
            return new TypeMapping("Boolean", "select");
        }
        return new TypeMapping("String", "input");
    }

    private record TypeMapping(String javaType, String formType) {}
}
