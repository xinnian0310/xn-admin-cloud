package com.smartadmin.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ImportResultVO {

    private int success;
    private int failed;
    private List<ImportErrorItem> errors = new ArrayList<>();

    @Data
    public static class ImportErrorItem {
        private int row;
        private String message;

        public ImportErrorItem(int row, String message) {
            this.row = row;
            this.message = message;
        }
    }

    public void addError(int row, String message) {
        failed++;
        errors.add(new ImportErrorItem(row, message));
    }

    public void addSuccess() {
        success++;
    }
}
