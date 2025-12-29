// RuleViolation.java
package com.fit2cloud.fapiao.dto.response;

import lombok.Data;

@Data
public class RuleViolation {
    public enum Severity {
        INFO,
        ERROR,    // 硬性错误，禁止提交
        WARNING   // 软性警告，需要备注说明
    }

    private String field;      // 违规字段
    private String affectedField; // 影响字段，如："购买方名称"、"开票日期"、"消费事由"
    private String message;    // 违规信息
    private Severity severity; // 严重程度

    public RuleViolation(String field, String message, Severity severity) {
        this.field = field;
        this.message = message;
        this.severity = severity;
        // 根据field推断affectedField
        this.affectedField = inferAffectedField(field);
    }

    // 新构造函数
    public RuleViolation(String field, String affectedField, String message, Severity severity) {
        this.field = field;
        this.affectedField = affectedField;
        this.message = message;
        this.severity = severity;
    }

    // 根据field推断affectedField
    private String inferAffectedField(String field) {
        switch (field) {
            case "buyer_info":
                return "购买方信息";
            case "invoice_date":
                return "开票日期";
            case "compliance_check":
                return "消费事由";
            default:
                return field;
        }
    }
}