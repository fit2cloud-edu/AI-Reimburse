// ValidationResult.java
package com.fit2cloud.fapiao.dto.response;

import lombok.Data;
import java.util.List;

/**
 * 验证结果数据传输对象
 * 用于封装发票验证的结果信息
 */
@Data
public class ValidationResult {
    /**
     * 验证是否通过
     * true表示验证通过，false表示验证不通过
     */
    private boolean valid;

    /**
     * 违规规则列表
     * 当验证不通过时，包含具体的违规信息
     */
    private List<RuleViolation> violations;

    /**
     * 真伪验证结果（新增）
     */
    private VerificationResult verificationResult;

    /**
     * 构造函数
     * @param valid 验证结果状态
     * @param violations 违规规则列表
     */
    public ValidationResult(boolean valid, List<RuleViolation> violations) {
        this.valid = valid;
        this.violations = violations;
    }

    /**
     * 全参构造函数（新增）
     * @param valid 验证结果状态
     * @param violations 违规规则列表
     * @param verificationResult 真伪验证结果
     */
    public ValidationResult(boolean valid, List<RuleViolation> violations,
                            VerificationResult verificationResult) {
        this.valid = valid;
        this.violations = violations;
        this.verificationResult = verificationResult;
    }

    /**
     * 无参构造函数（Lombok需要）
     */
    public ValidationResult() {
    }
}
