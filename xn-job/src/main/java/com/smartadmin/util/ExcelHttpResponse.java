package com.smartadmin.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/** 统一 xlsx 下载响应。 */
public final class ExcelHttpResponse {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private ExcelHttpResponse() {
    }

    public static ResponseEntity<byte[]> xlsx(byte[] body, String filename) {
        String safe = filename == null || filename.isBlank() ? "export.xlsx" : filename;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + safe + "\"; filename*=UTF-8''"
                                + java.net.URLEncoder.encode(safe, StandardCharsets.UTF_8))
                .contentType(XLSX)
                .body(body);
    }
}
