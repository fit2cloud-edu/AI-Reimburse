// InvoiceVerificationService.java
package com.fit2cloud.fapiao.service;

import com.fit2cloud.fapiao.dto.response.InvoiceInfo;
import com.fit2cloud.fapiao.dto.response.VerificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Slf4j
public class InvoiceVerificationService {

    @Value("${invoice.verification.api.host:https://fapiao.market.alicloudapi.com}")
    private String apiHost;

    @Value("${invoice.verification.api.path:/v2/invoice/query}")
    private String apiPath;

    @Value("${invoice.verification.appcode:}")
    private String appCode;

    @Value("${invoice.verification.enabled:true}")
    private boolean verificationEnabled;

    private final RestTemplate restTemplate;

    public InvoiceVerificationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 验证发票真伪
     */
    public VerificationResult verifyInvoice(InvoiceInfo invoice) {
        String identifier = generateInvoiceIdentifier(invoice);
        log.info("开始验证发票: {}", identifier);

        if (!verificationEnabled) {
            log.warn("真伪验证功能未启用");
            return VerificationResult.skip("真伪验证功能未启用");
        }

        // 必填字段检查
        if (!hasRequiredFields(invoice)) {
            log.warn("发票缺少必填字段: 号码={}, 日期={}",
                    invoice.getInvoiceNumber(), invoice.getInvoiceDate());
            return VerificationResult.skip("发票缺少必填字段（发票号码和开票日期）");
        }

        try {
            // 构建请求参数
            Map<String, String> requestParams = buildRequestParams(invoice);

            // 检查参数是否足够
            if (requestParams.size() < 2) {
                log.warn("构建的参数不足，跳过验证: params={}", requestParams);
                return VerificationResult.skip("构建的参数不足");
            }

            log.info("调用验证API，参数: {}", requestParams);

            // 发送验证请求
            Map<String, Object> response = callVerificationApi(requestParams);

            // 解析响应结果
            VerificationResult result = parseVerificationResult(response, invoice);
            log.info("验证结果: status={}, message={}", result.getStatus(), result.getMessage());

            return result;

        } catch (Exception e) {
            log.error("发票真伪验证失败: identifier={}, error={}",
                    identifier, e.getMessage(), e);
            return VerificationResult.error("真伪验证服务异常: " + e.getMessage());
        }
    }

    /**
     * 检查是否有必填字段
     * 根据API文档，必填字段是：fphm(发票号码)和kprq(开票日期)
     */
    private boolean hasRequiredFields(InvoiceInfo invoice) {
        if (invoice == null) {
            log.warn("发票对象为空");
            return false;
        }

        boolean hasNumber = invoice.getInvoiceNumber() != null &&
                !invoice.getInvoiceNumber().trim().isEmpty();
        boolean hasDate = invoice.getInvoiceDate() != null &&
                !invoice.getInvoiceDate().trim().isEmpty();

        log.debug("发票字段检查: 号码={}, 日期={}, 结果={}",
                hasNumber ? invoice.getInvoiceNumber() : "空",
                hasDate ? invoice.getInvoiceDate() : "空",
                hasNumber && hasDate);

        return hasNumber && hasDate;
    }

    /**
     * 生成发票标识符（用于日志记录）
     */
    private String generateInvoiceIdentifier(InvoiceInfo invoice) {
        List<String> parts = new ArrayList<>();

        if (invoice.getInvoiceNumber() != null && !invoice.getInvoiceNumber().isEmpty()) {
            parts.add("号码:" + invoice.getInvoiceNumber());
        }

        if (invoice.getInvoiceDate() != null && !invoice.getInvoiceDate().isEmpty()) {
            parts.add("日期:" + invoice.getInvoiceDate());
        }

        if (invoice.getTotalAmount() != null && !invoice.getTotalAmount().isEmpty()) {
            parts.add("金额:" + invoice.getTotalAmount());
        }

        return parts.isEmpty() ? "未知发票" : String.join(" ", parts);
    }

    /**
     * 构建API请求参数
     */
    private Map<String, String> buildRequestParams(InvoiceInfo invoice) {
        Map<String, String> params = new HashMap<>();

        // 必填参数
        params.put("fphm", invoice.getInvoiceNumber().trim());

        // 转换日期格式为YYYYMMDD
        String kprq = convertDateFormat(invoice.getInvoiceDate());
        if (kprq != null) {
            params.put("kprq", kprq);
        }

        // 添加金额（必需参数）
        addAmountParam(invoice, params);

        // 可选参数
        addOptionalParams(invoice, params);

        log.info("构建验证参数 - 发票号码: {}, 日期: {}, 参数: {}",
                invoice.getInvoiceNumber(), kprq, params);
        return params;
    }

    /**
     * 添加金额参数 - 改进版
     */
    private void addAmountParam(InvoiceInfo invoice, Map<String, String> params) {
        if (invoice.getTotalAmount() != null && !invoice.getTotalAmount().isEmpty()) {
            String cleanedAmount = cleanAmountString(invoice.getTotalAmount());
            if (cleanedAmount != null && !cleanedAmount.isEmpty()) {
                try {
                    // 验证并格式化金额
                    double amount = Double.parseDouble(cleanedAmount);
                    params.put("jshj", String.format("%.2f", amount));
                    log.info("成功添加金额参数: {}", cleanedAmount);
                } catch (NumberFormatException e) {
                    log.warn("金额格式无效，使用原始值: {}", invoice.getTotalAmount());
                    // 即使格式不对也尝试发送
                    params.put("jshj", cleanedAmount);
                }
            } else {
                log.warn("无法清理金额字符串: {}", invoice.getTotalAmount());
            }
        } else {
            log.warn("发票金额为空，可能影响验证结果");
        }
    }

    /**
     * 清理金额字符串
     */
    private String cleanAmountString(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            return null;
        }

        // 移除所有非数字字符（除了小数点和负号）
        String cleaned = amountStr
                .replace("元", "")
                .replace("¥", "")
                .replace("￥", "")
                .replace("RMB", "")
                .replace("CNY", "")
                .replace(",", "")
                .replace("，", "")
                .replace(" ", "")
                .trim();

        // 确保格式正确
        if (cleaned.matches("-?\\d+(\\.\\d+)?")) {
            return cleaned;
        }

        // 如果格式不正确，尝试提取数字
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("-?\\d+(\\.\\d+)?");
        java.util.regex.Matcher matcher = pattern.matcher(amountStr);
        if (matcher.find()) {
            return matcher.group();
        }

        return null;
    }

    /**
     * 转换日期格式为YYYYMMDD
     */
    private String convertDateFormat(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        try {
            // 清理日期字符串
            dateStr = dateStr.trim();

            // 尝试多种日期格式
            DateTimeFormatter[] formatters = {
                    DateTimeFormatter.ISO_DATE,                       // YYYY-MM-DD
                    DateTimeFormatter.ofPattern("yyyy/MM/dd"),       // YYYY/MM/DD
                    DateTimeFormatter.ofPattern("yyyy年MM月dd日"),    // YYYY年MM月DD日
                    DateTimeFormatter.ofPattern("yyyy.MM.dd"),       // YYYY.MM.DD
                    DateTimeFormatter.ofPattern("yyyyMMdd"),         // YYYYMMDD（已经是目标格式）
                    DateTimeFormatter.ofPattern("dd/MM/yyyy"),       // DD/MM/YYYY
                    DateTimeFormatter.ofPattern("MM/dd/yyyy")        // MM/DD/YYYY
            };

            for (DateTimeFormatter formatter : formatters) {
                try {
                    LocalDate date = LocalDate.parse(dateStr, formatter);
                    // 转换为目标格式YYYYMMDD
                    return date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                } catch (Exception e) {
                    // 继续尝试下一个格式
                    continue;
                }
            }

            // 如果是消费日期，也尝试转换
            if (dateStr.length() >= 8) {
                try {
                    // 尝试直接提取前8位数字
                    String cleanDate = dateStr.replaceAll("[^0-9]", "");
                    if (cleanDate.length() >= 8) {
                        String datePart = cleanDate.substring(0, 8);
                        LocalDate date = LocalDate.parse(datePart,
                                DateTimeFormatter.ofPattern("yyyyMMdd"));
                        return date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                    }
                } catch (Exception e) {
                    // 忽略解析错误
                }
            }

            log.warn("无法解析日期格式: {}", dateStr);
            return null;

        } catch (Exception e) {
            log.warn("日期格式转换失败: {}, error: {}", dateStr, e.getMessage());
            return dateStr.replaceAll("[^0-9]", "");
        }
    }

    /**
     * 添加可选参数
     */
    private void addOptionalParams(InvoiceInfo invoice, Map<String, String> params) {
        // 1. 价税合计（如果有的话）
        if (invoice.getTotalAmount() != null && !invoice.getTotalAmount().isEmpty()) {
            try {
                // 清理金额字符串，移除中文单位和逗号
                String amountStr = invoice.getTotalAmount()
                        .replace("元", "")
                        .replace("¥", "")
                        .replace("￥", "")
                        .replace(",", "")
                        .trim();

                if (!amountStr.isEmpty()) {
                    // 验证是否是有效数字
                    Double.parseDouble(amountStr);
                    params.put("jshj", amountStr);
                }
            } catch (NumberFormatException e) {
                log.warn("金额格式无效: {}", invoice.getTotalAmount());
            }
        }

        // 2. 校验码（如果有的话）
        String checkCode = extractCheckCode(invoice);
        if (checkCode != null && checkCode.length() >= 6) {
            params.put("checkCode", checkCode);
        }

        // 3. 发票代码（如果有的话）
        String invoiceCode = extractInvoiceCode(invoice);
        if (invoiceCode != null) {
            params.put("fpdm", invoiceCode);
        }
    }

    /**
     * 尝试提取校验码
     */
    private String extractCheckCode(InvoiceInfo invoice) {
        // 优先检查complianceCheck字段
        if (invoice.getComplianceCheck() != null && !invoice.getComplianceCheck().isEmpty()) {
            String text = invoice.getComplianceCheck();
            // 匹配6位数字的校验码
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b(\\d{6})\\b");
            java.util.regex.Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        // 检查其他可能包含校验码的字段
        String[] fields = {
                invoice.getRemark(),
                invoice.getInvoiceRemark()
        };

        for (String field : fields) {
            if (field != null && !field.isEmpty()) {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b(\\d{6})\\b");
                java.util.regex.Matcher matcher = pattern.matcher(field);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }

        return null;
    }

    /**
     * 尝试提取发票代码
     */
    private String extractInvoiceCode(InvoiceInfo invoice) {
        // 发票代码通常为10-12位数字
        String[] fields = {
                invoice.getComplianceCheck(),
                invoice.getRemark(),
                invoice.getInvoiceRemark()
        };

        for (String field : fields) {
            if (field != null && !field.isEmpty()) {
                // 匹配10-12位连续数字
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b(\\d{10,12})\\b");
                java.util.regex.Matcher matcher = pattern.matcher(field);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }

        return null;
    }

    /**
     * 调用验证API
     */
    private Map<String, Object> callVerificationApi(Map<String, String> requestParams) {
        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "APPCODE " + appCode);

        // 构建请求体
        String requestBody = buildFormUrlEncodedBody(requestParams);

        // 构建完整URL
        String url = UriComponentsBuilder.fromHttpUrl(apiHost)
                .path(apiPath)
                .build()
                .toUriString();

        log.debug("调用验证API: url={}, params={}", url, requestParams);

        try {
            // 发送请求
            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    url, HttpMethod.POST, requestEntity, Map.class);

            log.debug("验证API响应: status={}, body={}",
                    responseEntity.getStatusCode(), responseEntity.getBody());

            log.debug("验证API响应: status={}", responseEntity.getStatusCode());
            return responseEntity.getBody();
        } catch (Exception e) {
            log.error("验证API调用失败", e);
            throw new RuntimeException("验证API调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建表单编码的请求体
     */
    private String buildFormUrlEncodedBody(Map<String, String> params) {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (body.length() > 0) {
                body.append("&");
            }
            body.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return body.toString();
    }

    /**
     * 解析验证结果
     */
    private VerificationResult parseVerificationResult(Map<String, Object> response, InvoiceInfo invoice) {
        if (response == null) {
            return VerificationResult.error("验证服务返回空响应");
        }

        try {
            log.info("验证API返回: {}", response);

            // 检查常见的响应字段
            Integer errcode = (Integer) response.get("errcode");
            String message = (String) response.get("message");
            String errmsg = (String) response.get("errmsg");
            String status = (String) response.get("status");
            Boolean success = (Boolean) response.get("success");
            Object data = response.get("data");

            log.debug("验证API返回: errcode={}, message={}, status={}, success={}",
                    errcode, message, status, success);

            // 根据不同的API响应格式处理
            if (errcode != null) {
                // 格式1: 有errcode字段
                if (errcode == 0) {
                    // 验证成功
                    return VerificationResult.success("发票真伪验证通过",
                            data instanceof Map ? (Map<String, Object>) data : null);
                } else {
                    // 验证失败
                    return VerificationResult.failed("发票验证失败: " + message,
                            data instanceof Map ? (Map<String, Object>) data : null);
                }
            } else if (success != null) {
                // 格式2: 有success字段
                if (Boolean.TRUE.equals(success)) {
                    // 验证成功
                    return VerificationResult.success("发票真伪验证通过",
                            data instanceof Map ? (Map<String, Object>) data : null);
                } else {
                    // 验证失败
                    return VerificationResult.failed("发票验证失败: " + message,
                            data instanceof Map ? (Map<String, Object>) data : null);
                }
            } else if (status != null) {
                // 格式3: 有status字段
                if ("success".equalsIgnoreCase(status) || "true".equalsIgnoreCase(status)) {
                    return VerificationResult.success("发票真伪验证通过",
                            data instanceof Map ? (Map<String, Object>) data : null);
                } else {
                    return VerificationResult.failed("发票验证失败: " + message,
                            data instanceof Map ? (Map<String, Object>) data : null);
                }
            } else {
                // 未知格式，但可能有data字段
                if (data != null) {
                    // 假设有data就是成功
                    return VerificationResult.success("发票真伪验证通过",
                            data instanceof Map ? (Map<String, Object>) data : null);
                } else {
                    return VerificationResult.error("未知的API响应格式");
                }
            }

        } catch (Exception e) {
            log.error("解析验证响应失败", e);
            return VerificationResult.error("解析验证响应失败: " + e.getMessage());
        }
    }

    /**
     * 验证发票详细信息是否匹配
     */
    private boolean verifyInvoiceDetails(Map<String, Object> apiData, InvoiceInfo invoice) {
        try {
            // 对比关键信息：金额、购买方等
            Object apiAmountObj = apiData.get("jshj");  // 价税合计
            Double apiAmount = null;

            if (apiAmountObj != null) {
                if (apiAmountObj instanceof Double) {
                    apiAmount = (Double) apiAmountObj;
                } else if (apiAmountObj instanceof String) {
                    try {
                        apiAmount = Double.parseDouble(((String) apiAmountObj).replace(",", ""));
                    } catch (NumberFormatException e) {
                        log.warn("API返回金额格式错误: {}", apiAmountObj);
                    }
                }
            }

            Double invoiceAmount = null;
            if (invoice.getTotalAmount() != null) {
                try {
                    invoiceAmount = Double.parseDouble(invoice.getTotalAmount().replace(",", ""));
                } catch (NumberFormatException e) {
                    log.warn("发票金额格式错误: {}", invoice.getTotalAmount());
                }
            }

            if (apiAmount != null && invoiceAmount != null) {
                // 允许微小差异（如四舍五入）
                return Math.abs(apiAmount - invoiceAmount) < 0.01;
            }

            // 如果API没有返回金额，默认为匹配
            return true;
        } catch (Exception e) {
            log.warn("发票信息对比异常", e);
            return false;
        }
    }
}