package com.fit2cloud.fapiao.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class FileUploadResponse {
    private boolean success;
    private String message;
    private String fileId;
    private String fileName;
    private String chatContent;
    private List<InvoiceInfo> invoiceInfos;
    private String mediaIds;

    // 每日补贴金额（单位：元）
    private Integer dailySubsidyAmount;

    // 规则校验结果
    private BatchValidationResult validationResult;

    // 无参构造器
    public FileUploadResponse() {}

    // 全参构造器
    public FileUploadResponse(String fileId, String fileName, String chatContent) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.chatContent = chatContent;
        this.success = true;
        this.message = "处理完成";
    }
}