// VerificationResult.java
package com.fit2cloud.fapiao.dto.response;

import lombok.Data;
import java.util.Map;

@Data
public class VerificationResult {
    private boolean verified;      // 是否已验证
    private boolean valid;         // 是否有效
    private String status;         // 状态: SUCCESS, FAILED, ERROR, MISMATCH, SKIP
    private String message;        // 验证消息
    private Map<String, Object> apiData;  // API原始数据

    // 状态常量
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_ERROR = "ERROR";
    public static final String STATUS_MISMATCH = "MISMATCH";
    public static final String STATUS_SKIP = "SKIP";

    // 静态工厂方法
    public static VerificationResult success(String message) {
        return success(message, null);
    }

    public static VerificationResult success(String message, Map<String, Object> apiData) {
        VerificationResult result = new VerificationResult();
        result.setVerified(true);
        result.setValid(true);
        result.setStatus(STATUS_SUCCESS);
        result.setMessage(message);
        result.setApiData(apiData);
        return result;
    }

    public static VerificationResult failed(String message) {
        return failed(message, null);
    }

    public static VerificationResult failed(String message, Map<String, Object> apiData) {
        VerificationResult result = new VerificationResult();
        result.setVerified(true);
        result.setValid(false);
        result.setStatus(STATUS_FAILED);
        result.setMessage(message);
        result.setApiData(apiData);
        return result;
    }

    public static VerificationResult mismatch(String message, Map<String, Object> apiData) {
        VerificationResult result = new VerificationResult();
        result.setVerified(true);
        result.setValid(false);
        result.setStatus(STATUS_MISMATCH);
        result.setMessage(message);
        result.setApiData(apiData);
        return result;
    }

    public static VerificationResult error(String message) {
        VerificationResult result = new VerificationResult();
        result.setVerified(false);
        result.setValid(false);
        result.setStatus(STATUS_ERROR);
        result.setMessage(message);
        return result;
    }

    public static VerificationResult skip(String message) {
        VerificationResult result = new VerificationResult();
        result.setVerified(false);
        result.setValid(true);  // 跳过验证默认认为有效
        result.setStatus(STATUS_SKIP);
        result.setMessage(message);
        return result;
    }
}