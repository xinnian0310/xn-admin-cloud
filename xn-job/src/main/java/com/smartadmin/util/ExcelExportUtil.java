package com.smartadmin.util;

import com.alibaba.excel.EasyExcel;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** EasyExcel xlsx 导出（表头 + 字符串行列）。 */
public final class ExcelExportUtil {

    private ExcelExportUtil() {}

    public static byte[] toXlsx(String sheetName, List<String> headers, List<List<String>> rows) {
        List<List<String>> head = new ArrayList<>();
        if (headers != null) {
            for (String h : headers) {
                head.add(Collections.singletonList(h == null ? "" : h));
            }
        }
        List<List<String>> data = rows == null ? List.of() : rows;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            EasyExcel.write(out)
                    .head(head)
                    .sheet(sheetName == null || sheetName.isBlank() ? "Sheet1" : sheetName)
                    .doWrite(data);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("导出 Excel 失败: " + ex.getMessage(), ex);
        }
    }
}
