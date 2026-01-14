package com.fit2cloud.fapiao.dto.response;

import lombok.Data;

@Data
public class DuplicateCheckResult {
    private boolean isDuplicate;
    private String duplicateReason;
    private String invoiceNumber;
    private String invoiceDate;
    private String userId;
    private String checkStrategy;

    public DuplicateCheckResult() {
    }

    public DuplicateCheckResult(boolean isDuplicate, String duplicateReason, String invoiceNumber, String invoiceDate, String userId, String checkStrategy) {
        this.isDuplicate = isDuplicate;
        this.duplicateReason = duplicateReason;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.userId = userId;
        this.checkStrategy = checkStrategy;
    }

    public static DuplicateCheckResult notDuplicate(String invoiceNumber, String invoiceDate, String userId, String checkStrategy) {
        return new DuplicateCheckResult(false, "发票查重通过", invoiceNumber, invoiceDate, userId, checkStrategy);
    }

    public static DuplicateCheckResult duplicate(String duplicateReason, String invoiceNumber, String invoiceDate, String userId, String checkStrategy) {
        return new DuplicateCheckResult(true, duplicateReason, invoiceNumber, invoiceDate, userId, checkStrategy);
    }
}