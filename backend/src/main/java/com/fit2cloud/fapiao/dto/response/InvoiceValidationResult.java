// InvoiceValidationResult.java
package com.fit2cloud.fapiao.dto.response;

import lombok.Data;

/**
 * 发票验证结果数据传输对象
 * 用于封装单张发票的验证结果信息，包括发票基本信息和验证详情
 */
@Data
public class InvoiceValidationResult {
    /**
     * 发票在列表中的索引位置
     * 用于标识该发票在原始发票列表中的顺序位置
     */
    private int invoiceIndex;

    /**
     * 发票信息
     * 包含发票的基本信息，如发票代码、发票号码、开票日期等
     */
    private InvoiceInfo invoice;

    /**
     * 校验结果
     * 包含该发票的验证状态和违规详情
     */
    private ValidationResult validationResult;

    /**
     * 构造函数
     * @param invoiceIndex 发票在列表中的索引
     * @param invoice 发票信息对象
     * @param validationResult 发票验证结果对象
     */
    public InvoiceValidationResult(int invoiceIndex, InvoiceInfo invoice, ValidationResult validationResult) {
        this.invoiceIndex = invoiceIndex;
        this.invoice = invoice;
        this.validationResult = validationResult;
    }
}
