// BatchValidationResult.java
package com.fit2cloud.fapiao.dto.response;

import lombok.Data;
import java.util.List;

/**
 * 批量验证结果数据传输对象
 * 用于封装多张发票批量验证的结果信息
 */
@Data
public class BatchValidationResult {
    /**
     * 所有发票是否都通过校验
     * true表示所有发票都验证通过，false表示至少有一张发票验证不通过
     */
    private boolean allValid;

    /**
     * 是否存在硬性错误
     * true表示存在硬性错误（如发票格式严重错误），false表示没有硬性错误
     */
    private boolean hasHardErrors;

    /**
     * 每张发票的校验结果列表
     * 包含每张发票的具体验证结果和违规信息
     */
    private List<InvoiceValidationResult> results;

    /**
     * 构造函数
     * @param allValid 所有发票是否都通过校验
     * @param hasHardErrors 是否存在硬性错误
     * @param results 每张发票的校验结果列表
     */
    public BatchValidationResult(boolean allValid, boolean hasHardErrors,
                                 List<InvoiceValidationResult> results) {
        this.allValid = allValid;
        this.hasHardErrors = hasHardErrors;
        this.results = results;
    }
}
