package com.fit2cloud.fapiao.dto.response;

import lombok.Data;

@Data
public class InvoiceInfo {
    private String invoiceItemName;
    private String totalAmount;
    private String buyerName;
    private String buyerCode;
    private String sellerName;
    private String invoiceNumber;
    private String invoiceDate;
    private String hasSeal;
    private String remark;
    private String reimbursementType;
    private String reimbursementReason;

    // 添加子报销类型字段
    private String consumptionReason; // 消费事由
    private String subReimbursementType; // 子类型
    private String consumptionDate; // 消费日期（兼容字段）

    // 前端需要的字段
    private String reimbursementTypeInput; // 用户选择的报销类型
    private String remarkInput; // 用户输入的备注
    private String invoiceRemark; // 发票备注

    //合规检查字段
    private String complianceCheck;

}