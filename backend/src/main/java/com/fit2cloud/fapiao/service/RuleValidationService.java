// RuleValidationService.java
package com.fit2cloud.fapiao.service;

import com.fit2cloud.fapiao.dto.response.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class RuleValidationService {
    @Autowired
    private UserInfoService userInfoService;

    @Autowired
    private InvoiceVerificationService verificationService;

    @Autowired
    private RestTemplate restTemplate;  // 需要在配置类中注入

    // 发票验证API配置（从配置文件读取）
    @Value("${invoice.verification.api.host:https://fapiao.market.alicloudapi.com}")
    private String verificationApiHost;

    @Value("${invoice.verification.api.path:/v2/invoice/query}")
    private String verificationApiPath;

    @Value("${invoice.verification.appcode:}")
    private String verificationAppCode;

    @Value("${invoice.verification.enabled:true}")
    private boolean verificationEnabled;

    // 公司标准信息
    private static final String COMPANY_NAME = "杭州飞致云信息科技有限公司";
    private static final String COMPANY_TAX_CODE = "91330106311245339J";

    // 合规标准
    private static final String[] VALID_ACCOMMODATION = {"华住-汉庭", "华住-宜必思", "华住-你好酒店", "华住-怡莱酒店"};
    private static final String[] VALID_TRANSPORTATION = {"高铁-二等座", "飞机-经济舱", "火车-动卧"};

    /**
     * 单张发票规则校验
     */
    public ValidationResult validateInvoice(InvoiceInfo invoice, String formType) {
        List<RuleViolation> violations = new ArrayList<>();
        VerificationResult verificationResult = null;

        // 生成发票标识（用于日志）
        String identifier = generateInvoiceIdentifier(invoice);
        log.info("开始整体验证: {}", identifier);

        // 第一步：真伪验证（如果启用）
        if (verificationEnabled) {
            verificationResult = verificationService.verifyInvoice(invoice);
            handleVerificationResult(verificationResult, invoice, violations);
        } else {
            log.debug("发票真伪验证未启用");
            verificationResult = VerificationResult.skip("真伪验证未启用");
        }

        // 第二步：规则校验（只有在真伪验证通过或跳过时才进行）
        boolean shouldProceedRuleValidation = verificationResult == null ||
                !verificationResult.isVerified() ||
                verificationResult.isValid();

        if (shouldProceedRuleValidation) {
            // 1. 购买方信息校验
            validateBuyerInfo(invoice, violations);

            // 2. 开票日期校验
            validateInvoiceDate(invoice, violations);

            // 3. 合规检查校验（仅对客成差旅报销）
            if ("客成差旅报销单".equals(formType)) {
                validateCompliance(invoice, violations);
            }
        } else {
            log.warn("由于真伪验证失败，跳过规则校验: identifier={}", identifier);
        }

        // 构建验证结果
        ValidationResult result = new ValidationResult(violations.isEmpty(), violations);
        result.setVerificationResult(verificationResult);

        log.info("发票验证完成: identifier={}, 真伪验证状态={}, 规则验证={}",
                identifier,
                verificationResult != null ? verificationResult.getStatus() : "null",
                violations.isEmpty() ? "通过" : "失败");

        return result;
    }

    /**
     * 生成发票标识符（用于日志记录）
     */
    private String generateInvoiceIdentifier(InvoiceInfo invoice) {
        StringBuilder identifier = new StringBuilder();

        if (invoice.getInvoiceNumber() != null && !invoice.getInvoiceNumber().isEmpty()) {
            identifier.append("发票号码:").append(invoice.getInvoiceNumber());
        }

        if (invoice.getInvoiceDate() != null && !invoice.getInvoiceDate().isEmpty()) {
            if (identifier.length() > 0) {
                identifier.append(", ");
            }
            identifier.append("日期:").append(invoice.getInvoiceDate());
        }

        if (invoice.getTotalAmount() != null && !invoice.getTotalAmount().isEmpty()) {
            if (identifier.length() > 0) {
                identifier.append(", ");
            }
            identifier.append("金额:").append(invoice.getTotalAmount());
        }

        if (invoice.getBuyerName() != null && !invoice.getBuyerName().isEmpty()) {
            if (identifier.length() > 0) {
                identifier.append(", ");
            }
            identifier.append("购买方:").append(invoice.getBuyerName());
        }

        // 如果没有任何信息，使用默认标识
        if (identifier.length() == 0) {
            return "未知发票";
        }

        return identifier.toString();
    }


    /**
     * 验证发票详细信息是否匹配（增强版）
     */
    private boolean verifyInvoiceDetails(Map<String, Object> apiData, InvoiceInfo invoice) {
        try {
            boolean allMatch = true;
            List<String> mismatchReasons = new ArrayList<>();

            // 1. 验证发票号码
            String apiInvoiceNo = getStringValue(apiData, "fphm", "invoiceNumber");
            if (apiInvoiceNo != null && invoice.getInvoiceNumber() != null) {
                if (!apiInvoiceNo.equals(invoice.getInvoiceNumber())) {
                    mismatchReasons.add(String.format("发票号码不匹配: API=%s, 识别=%s",
                            apiInvoiceNo, invoice.getInvoiceNumber()));
                    allMatch = false;
                }
            }

            // 2. 验证金额（如果有）
            Object apiAmountObj = apiData.get("jshj");
            if (apiAmountObj != null && invoice.getTotalAmount() != null) {
                try {
                    double apiAmount = parseDouble(apiAmountObj);
                    double invoiceAmount = parseDouble(invoice.getTotalAmount());

                    if (Math.abs(apiAmount - invoiceAmount) > 0.01) {
                        mismatchReasons.add(String.format("金额不匹配: API=%.2f, 识别=%.2f",
                                apiAmount, invoiceAmount));
                        allMatch = false;
                    }
                } catch (NumberFormatException e) {
                    log.warn("金额解析失败: API={}, 识别={}",
                            apiAmountObj, invoice.getTotalAmount());
                }
            }

            // 3. 验证购买方信息（如果有）
            String apiBuyerName = getStringValue(apiData, "buyername", "buyerName");
            if (apiBuyerName != null && invoice.getBuyerName() != null) {
                // 简单对比，忽略空格和标点
                String cleanApiName = apiBuyerName.replaceAll("[\\s\\pP]", "");
                String cleanInvoiceName = invoice.getBuyerName().replaceAll("[\\s\\pP]", "");

                if (!cleanApiName.equals(cleanInvoiceName) &&
                        !cleanApiName.contains(cleanInvoiceName) &&
                        !cleanInvoiceName.contains(cleanApiName)) {
                    // 不直接标记为不匹配，因为可能有简称等情况
                    log.debug("购买方名称有差异: API={}, 识别={}",
                            apiBuyerName, invoice.getBuyerName());
                }
            }

            // 4. 验证销售方信息（如果有）
            String apiSellerName = getStringValue(apiData, "sellername", "sellerName");
            if (apiSellerName != null && invoice.getSellerName() != null) {
                // 简单对比
                String cleanApiSeller = apiSellerName.replaceAll("[\\s\\pP]", "");
                String cleanInvoiceSeller = invoice.getSellerName().replaceAll("[\\s\\pP]", "");

                if (!cleanApiSeller.equals(cleanInvoiceSeller)) {
                    mismatchReasons.add("销售方名称不匹配");
                    allMatch = false;
                }
            }

            if (!allMatch && !mismatchReasons.isEmpty()) {
                log.warn("发票信息不匹配: {}", String.join("; ", mismatchReasons));
            }

            return allMatch;

        } catch (Exception e) {
            log.warn("发票信息对比异常", e);
            // 对比异常时不标记为不匹配，避免误判
            return true;
        }
    }

    /**
     * 安全的字符串值获取
     */
    private String getStringValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                Object value = map.get(key);
                if (value != null) {
                    return value.toString().trim();
                }
            }
        }
        return null;
    }

    /**
     * 解析Double值
     */
    private double parseDouble(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        } else if (obj instanceof String) {
            String str = ((String) obj).replace(",", "").trim();
            return Double.parseDouble(str);
        }
        throw new NumberFormatException("无法解析为double: " + obj);
    }

    /**
     * 处理真伪验证结果
     */
    private void handleVerificationResult(VerificationResult verificationResult,
                                          InvoiceInfo invoice,
                                          List<RuleViolation> violations) {
        if (verificationResult == null) {
            return;
        }

        if (!verificationResult.isVerified()) {
            // 未验证（跳过或错误）
            if (VerificationResult.STATUS_ERROR.equals(verificationResult.getStatus())) {
                violations.add(new RuleViolation(
                        "invoice_verification",
                        "发票真伪",
                        verificationResult.getMessage(),
                        RuleViolation.Severity.WARNING
                ));
            }
            return;
        }

        // 已验证，检查结果
        if (!verificationResult.isValid()) {
            String message = verificationResult.getMessage();
            RuleViolation.Severity severity = switch (verificationResult.getStatus()) {
                case VerificationResult.STATUS_FAILED ->
                        RuleViolation.Severity.ERROR;  // 验证失败是严重错误
                case VerificationResult.STATUS_MISMATCH ->
                        RuleViolation.Severity.ERROR;  // 信息不匹配也是错误
                default -> RuleViolation.Severity.WARNING;
            };

            violations.add(new RuleViolation(
                    "invoice_verification",
                    "发票真伪",
                    message,
                    severity
            ));

            String identifier = generateInvoiceIdentifier(invoice);
            log.warn("发票真伪验证失败: identifier={}, message={}",
                    identifier, message);
        } else {
            String identifier = generateInvoiceIdentifier(invoice);
            log.info("发票真伪验证通过: identifier={}", identifier);
        }
    }

    /**
     * 判断是否继续进行规则校验
     */
    private boolean shouldProceedRuleValidation(VerificationResult verificationResult) {
        // 如果真伪验证失败，停止后续规则校验
        if (verificationResult.isVerified() && !verificationResult.isValid()) {
            return false;
        }
        return true;
    }

    /**
     * 批量发票规则校验
     */
    public BatchValidationResult validateInvoices(List<InvoiceInfo> invoices, String formType) {
        List<InvoiceValidationResult> results = new ArrayList<>();
        boolean allValid = true;
        boolean hasHardErrors = false;

        for (int i = 0; i < invoices.size(); i++) {
            InvoiceInfo invoice = invoices.get(i);
            ValidationResult result = validateInvoice(invoice, formType);
            results.add(new InvoiceValidationResult(i, invoice, result));

            if (!result.isValid()) {
                allValid = false;
                // 检查是否有硬性错误
                if (hasHardError(result.getViolations())) {
                    hasHardErrors = true;
                }
            }
        }

        return new BatchValidationResult(allValid, hasHardErrors, results);
    }

    /**
     * 合规检查校验
     */
    private void validateCompliance(InvoiceInfo invoice, List<RuleViolation> violations) {
        // 从合规检查字段获取信息（需要InvoiceInfo中添加complianceCheck字段）
        String complianceCheck = invoice.getComplianceCheck();
        if (complianceCheck == null || complianceCheck.trim().isEmpty()) {
            return;
        }

        String invoiceItemName = invoice.getInvoiceItemName();
        boolean isValid = false;

        // 住宿费合规检查
        if (invoiceItemName != null && invoiceItemName.contains("住宿")) {
            validateAccommodationCompliance(complianceCheck, violations);
        }
        // 交通费合规检查
        else if (invoiceItemName != null && invoiceItemName.contains("交通")) {
            validateTransportationCompliance(complianceCheck, violations);
        }
        // 差旅成本合规检查（新增）
        else if (invoiceItemName != null && invoiceItemName.contains("差旅")) {
            validateTravelCompliance(complianceCheck, violations);
        }
    }


    /**
     * 住宿合规检查
     */
    private void validateAccommodationCompliance(String complianceCheck, List<RuleViolation> violations) {
        boolean isValid = false;
        String matchedHotel = null;

        for (String validAccommodation : VALID_ACCOMMODATION) {
            if (complianceCheck.contains(validAccommodation)) {
                isValid = true;
                matchedHotel = validAccommodation;
                break;
            }
        }

        if (!isValid) {
            // 提取实际的酒店名称
            String actualHotel = extractHotelName(complianceCheck);
            String message = String.format("住宿酒店'%s'不符合优选标准（可选酒店：%s）",
                    actualHotel != null ? actualHotel : complianceCheck,
                    String.join("、", VALID_ACCOMMODATION));

            violations.add(new RuleViolation(
                    "compliance_check",
                    "消费事由",  // 影响字段 - 新增
                    message,
                    RuleViolation.Severity.WARNING
            ));
        }
    }

    /**
     * 交通合规检查
     */
    private void validateTransportationCompliance(String complianceCheck, List<RuleViolation> violations) {
        boolean isValid = false;
        String matchedTransport = null;

        for (String validTransport : VALID_TRANSPORTATION) {
            if (complianceCheck.contains(validTransport)) {
                isValid = true;
                matchedTransport = validTransport;
                break;
            }
        }

        if (!isValid) {
            // 分析具体问题
            String detailMessage = analyzeTransportationIssue(complianceCheck);

            violations.add(new RuleViolation(
                    "compliance_check",
                    "消费事由",  // 影响字段 - 新增
                    detailMessage,
                    RuleViolation.Severity.WARNING
            ));
        }
    }

    /**
     * 差旅成本合规检查
     */
    private void validateTravelCompliance(String complianceCheck, List<RuleViolation> violations) {
        // 差旅成本可能包含交通和住宿信息
        boolean hasTransportIssue = false;
        boolean hasAccommodationIssue = false;

        // 检查是否包含交通信息
        if (complianceCheck.contains("高铁") || complianceCheck.contains("飞机") ||
                complianceCheck.contains("火车") || complianceCheck.contains("动车")) {
            hasTransportIssue = validateTransportationComplianceInternal(complianceCheck, violations);
        }

        // 检查是否包含住宿信息
        if (complianceCheck.contains("酒店") || complianceCheck.contains("宾馆") ||
                complianceCheck.contains("住宿")) {
            hasAccommodationIssue = validateAccommodationComplianceInternal(complianceCheck, violations);
        }
    }

    /**
     * 分析交通问题详情
     */
    private String analyzeTransportationIssue(String complianceCheck) {
        // 检查座位类型
        if (complianceCheck.contains("一等座")) {
            return String.format("交通标准'高铁-一等座'不符合要求，仅限二等座（可选：%s）",
                    String.join("、", VALID_TRANSPORTATION));
        } else if (complianceCheck.contains("商务舱") || complianceCheck.contains("头等舱")) {
            return String.format("交通标准'%s'不符合要求，仅限经济舱（可选：%s）",
                    extractTransportType(complianceCheck),
                    String.join("、", VALID_TRANSPORTATION));
        } else if (complianceCheck.contains("动车") && !complianceCheck.contains("动卧")) {
            return String.format("交通标准'%s'不符合要求（可选：%s）",
                    complianceCheck,
                    String.join("、", VALID_TRANSPORTATION));
        } else {
            return String.format("交通标准'%s'不符合要求（可选：%s）",
                    complianceCheck,
                    String.join("、", VALID_TRANSPORTATION));
        }
    }

    /**
     * 提取酒店名称
     */
    private String extractHotelName(String complianceCheck) {
        // 简单的提取逻辑
        if (complianceCheck.contains("-")) {
            return complianceCheck.split("-")[1];
        }
        return complianceCheck;
    }

    /**
     * 提取交通类型
     */
    private String extractTransportType(String complianceCheck) {
        // 简单的提取逻辑
        if (complianceCheck.contains("-")) {
            return complianceCheck;
        }
        return complianceCheck;
    }

    /**
     * 内部交通合规检查
     */
    private boolean validateTransportationComplianceInternal(String complianceCheck, List<RuleViolation> violations) {
        boolean isValid = false;
        for (String validTransport : VALID_TRANSPORTATION) {
            if (complianceCheck.contains(validTransport)) {
                isValid = true;
                break;
            }
        }

        if (!isValid) {
            String detailMessage = analyzeTransportationIssue(complianceCheck);
            violations.add(new RuleViolation(
                    "compliance_check",
                    "消费事由",
                    detailMessage,
                    RuleViolation.Severity.WARNING
            ));
            return true; // 表示有问题
        }
        return false; // 表示没问题
    }

    /**
     * 内部住宿合规检查
     */
    private boolean validateAccommodationComplianceInternal(String complianceCheck, List<RuleViolation> violations) {
        boolean isValid = false;
        for (String validAccommodation : VALID_ACCOMMODATION) {
            if (complianceCheck.contains(validAccommodation)) {
                isValid = true;
                break;
            }
        }

        if (!isValid) {
            String actualHotel = extractHotelName(complianceCheck);
            String message = String.format("住宿酒店'%s'不符合优选标准（可选酒店：%s）",
                    actualHotel != null ? actualHotel : complianceCheck,
                    String.join("、", VALID_ACCOMMODATION));

            violations.add(new RuleViolation(
                    "compliance_check",
                    "消费事由",
                    message,
                    RuleViolation.Severity.WARNING
            ));
            return true; // 表示有问题
        }
        return false; // 表示没问题
    }

    /**
     * 购买方信息校验（增强错误信息）
     */
    private void validateBuyerInfo(InvoiceInfo invoice, List<RuleViolation> violations) {
        String buyerName = invoice.getBuyerName();
        String buyerCode = invoice.getBuyerCode();

        boolean isCompanyName = COMPANY_NAME.equals(buyerName);
        boolean isPersonal = isPersonalName(buyerName);
        boolean isTaxCodeCorrect = COMPANY_TAX_CODE.equals(buyerCode);

        // 情况1：名称是公司
        if (isCompanyName) {
            // 子情况1a：公司名称正确，但税号错误 -> 标记“购买方代码”错误
            if (!isTaxCodeCorrect) {
                violations.add(new RuleViolation(
                        "buyer_info",
                        "购买方代码",
                        String.format("购买方代码'%s'与公司税号'%s'不匹配",
                                buyerCode != null ? buyerCode : "空",
                                COMPANY_TAX_CODE),
                        RuleViolation.Severity.ERROR
                ));
            }
            // 子情况1b：公司名称正确，税号也正确 -> 两者都通过，无需处理
        }
        // 情况2：名称是个人
        else if (isPersonal) {
            // 使用UserInfoService验证是否为企业成员
            try {
                if (buyerName != null && !buyerName.trim().isEmpty()) {
                    Map<String, Object> verificationResult = userInfoService.verifyEnterpriseMember(buyerName.trim());

                    boolean isEnterpriseMember = Boolean.TRUE.equals(verificationResult.get("isEnterpriseMember"));

                    if (isEnterpriseMember) {
                        // 是企业成员，添加信息提示（非错误）
                        List<Map<String, Object>> matchedUsers = (List<Map<String, Object>>) verificationResult.get("matchedUsers");
                        String memberInfo = buildMemberInfoMessage(matchedUsers);

                        violations.add(new RuleViolation(
                                "buyer_info",
                                "购买方名称",
                                String.format("购买方'%s'为企业员工%s",
                                        buyerName,
                                        memberInfo),
                                RuleViolation.Severity.INFO  // 注意：这里改为INFO级别，不是错误
                        ));

                        log.info("个人发票购买方为企业员工: {}, 详细信息: {}", buyerName, memberInfo);
                    } else {
                        // 不是企业成员，给出警告（可根据业务需要调整级别）
                        violations.add(new RuleViolation(
                                "buyer_info",
                                "购买方名称",
                                String.format("购买方'%s'非企业员工，请确认发票购买方身份",
                                        buyerName),
                                RuleViolation.Severity.ERROR  // 错误级别
                        ));

                        log.warn("个人发票购买方非企业员工: {}", buyerName);
                    }
                }
            } catch (Exception e) {
                // 如果验证服务出现问题，记录错误但不阻断流程
                log.error("企业成员验证失败，购买方: {}", buyerName, e);

                violations.add(new RuleViolation(
                        "buyer_info",
                        "购买方名称",
                        String.format("企业成员验证服务异常，无法验证'%s'是否为企业员工",
                                buyerName),
                        RuleViolation.Severity.WARNING
                ));
            }
        }
        // 情况3：名称不是公司也不是个人（即名称错误）
        else {
            // 名称错误是根本性问题
            violations.add(new RuleViolation(
                    "buyer_info",
                    "购买方名称", // 影响字段：名称
                    String.format("购买方名称'%s'不符合要求，应为'%s'或个人姓名",
                            buyerName != null ? buyerName : "空",
                            COMPANY_NAME),
                    RuleViolation.Severity.ERROR
            ));
            // 即使名称错了，如果代码也不对，也额外标记一下代码错误，提供更完整信息。
            // 但前端联动标红主要依赖“名称错误”这一个标记即可触发。
            if (buyerCode != null && !isTaxCodeCorrect) {
                violations.add(new RuleViolation(
                        "buyer_info",
                        "购买方代码", // 影响字段：代码
                        "购买方名称错误，且代码与公司税号不匹配",
                        RuleViolation.Severity.WARNING // 可设为WARNING，因为名称错误是主因
                ));
            }
        }
    }

    /**
     * 构建企业成员信息消息
     */
    private String buildMemberInfoMessage(List<Map<String, Object>> matchedUsers) {
        if (matchedUsers == null || matchedUsers.isEmpty()) {
            return "";
        }

        StringBuilder message = new StringBuilder("（匹配到");

        if (matchedUsers.size() == 1) {
            Map<String, Object> user = matchedUsers.get(0);
            String userId = (String) user.get("userId");
            String position = (String) user.get("position");
            List<String> departments = (List<String>) user.get("departmentNames");

            message.append("用户: ").append(userId != null ? userId : "未知ID");

            if (position != null && !position.isEmpty()) {
                message.append("，职位: ").append(position);
            }

            if (departments != null && !departments.isEmpty()) {
                message.append("，部门: ").append(String.join("/", departments));
            }

        } else {
            // 多个匹配的情况
            message.append("多个用户，请确认");

            // 列出所有匹配的用户ID
            List<String> userIds = new ArrayList<>();
            for (Map<String, Object> user : matchedUsers) {
                String userId = (String) user.get("userId");
                if (userId != null) {
                    userIds.add(userId);
                }
            }

            if (!userIds.isEmpty()) {
                message.append("：").append(String.join("、", userIds));
            }
        }

        message.append("）");
        return message.toString();
    }

    /**
     * 开票日期校验（增强错误信息）
     */
    private void validateInvoiceDate(InvoiceInfo invoice, List<RuleViolation> violations) {
        String invoiceDateStr = invoice.getInvoiceDate();
        if (invoiceDateStr == null || invoiceDateStr.trim().isEmpty()) {
            violations.add(new RuleViolation(
                    "invoice_date",
                    "开票日期",  // 影响字段
                    "开票日期为空",
                    RuleViolation.Severity.WARNING
            ));
            return;
        }

        try {
            LocalDate invoiceDate = LocalDate.parse(invoiceDateStr, DateTimeFormatter.ISO_DATE);
            LocalDate oneYearAgo = LocalDate.now().minusYears(1);
            LocalDate today = LocalDate.now();

            if (invoiceDate.isBefore(oneYearAgo)) {
                violations.add(new RuleViolation(
                        "invoice_date",
                        "开票日期",  // 影响字段
                        String.format("开票日期'%s'已超过一年有效期（最早允许：%s）",
                                invoiceDateStr,
                                oneYearAgo.format(DateTimeFormatter.ISO_DATE)),
                        RuleViolation.Severity.WARNING
                ));
            } else if (invoiceDate.isAfter(today)) {
                violations.add(new RuleViolation(
                        "invoice_date",
                        "开票日期",  // 影响字段
                        String.format("开票日期'%s'不能晚于今天（%s）",
                                invoiceDateStr,
                                today.format(DateTimeFormatter.ISO_DATE)),
                        RuleViolation.Severity.WARNING
                ));
            }
        } catch (DateTimeParseException e) {
            violations.add(new RuleViolation(
                    "invoice_date",
                    "开票日期",  // 影响字段
                    String.format("开票日期'%s'格式错误，应为YYYY-MM-DD格式", invoiceDateStr),
                    RuleViolation.Severity.WARNING
            ));
        }
    }


    /**
     * 判断是否为个人姓名
     */
    private boolean isPersonalName(String name) {
        if (name == null) return false;
        // 个人姓名特征：2-4个汉字，不包含公司关键词
        String[] companyKeywords = {"公司", "有限", "责任", "股份", "集团", "厂", "店", "局"};
        for (String keyword : companyKeywords) {
            if (name.contains(keyword)) {
                return false;
            }
        }
        return name.matches("^[\\u4e00-\\u9fa5]{2,4}$");
    }

    /**
     * 检查是否有硬性错误
     */
    private boolean hasHardError(List<RuleViolation> violations) {
        return violations.stream()
                .anyMatch(violation -> violation.getSeverity() == RuleViolation.Severity.ERROR);
    }
}