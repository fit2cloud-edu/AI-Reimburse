package com.fit2cloud.fapiao.service;

import com.fit2cloud.fapiao.dto.response.DuplicateCheckResult;
import com.fit2cloud.fapiao.dto.response.InvoiceInfo;
import com.fit2cloud.fapiao.entity.InvoiceDuplicateCheck;
import com.fit2cloud.fapiao.exception.BusinessException;
import com.fit2cloud.fapiao.repository.InvoiceDuplicateCheckRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Service
@Slf4j
public class InvoiceDuplicateCheckService {

    @Value("${invoice.duplicate.check.enabled:true}")
    private boolean duplicateCheckEnabled;

    @Value("${invoice.duplicate.check.strategy:STRICT}")
    private String duplicateCheckStrategy;

    private final InvoiceDuplicateCheckRepository duplicateCheckRepository;

    public InvoiceDuplicateCheckService(InvoiceDuplicateCheckRepository duplicateCheckRepository) {
        this.duplicateCheckRepository = duplicateCheckRepository;
    }

    /**
     * 检查发票是否重复
     */
    public DuplicateCheckResult checkDuplicate(InvoiceInfo invoice, String userId) {
        if (!duplicateCheckEnabled) {
            log.info("发票查重功能未启用，跳过检查");
            return DuplicateCheckResult.notDuplicate(null, null, userId, "DISABLED");
        }

        if (invoice == null || invoice.getInvoiceNumber() == null || invoice.getInvoiceDate() == null) {
            log.warn("发票信息不完整，无法进行查重检查");
            return DuplicateCheckResult.notDuplicate(null, null, userId, "INCOMPLETE_DATA");
        }

        String invoiceNumber = invoice.getInvoiceNumber().trim();
        LocalDate invoiceDate = parseInvoiceDate(invoice.getInvoiceDate());
        BigDecimal totalAmount = parseTotalAmount(invoice.getTotalAmount());

        if (invoiceDate == null) {
            log.warn("无法解析发票日期: {}", invoice.getInvoiceDate());
            return DuplicateCheckResult.notDuplicate(invoiceNumber, invoice.getInvoiceDate(), userId, "DATE_PARSE_ERROR");
        }

        boolean isDuplicate = false;
        String duplicateReason = "";

        // 根据策略进行查重
        switch (duplicateCheckStrategy.toUpperCase()) {
            case "STRICT":
                isDuplicate = duplicateCheckRepository.existsByInvoiceNumberAndInvoiceDate(invoiceNumber, invoiceDate);
                duplicateReason = "存在相同发票号码和开票日期的记录";
                break;

            case "NORMAL":
                if (totalAmount != null) {
                    isDuplicate = duplicateCheckRepository.existsByInvoiceNumberAndSimilarAmount(invoiceNumber, totalAmount);
                    duplicateReason = "存在相同发票号码和近似金额的记录";
                } else {
                    isDuplicate = duplicateCheckRepository.existsByInvoiceNumberAndInvoiceDate(invoiceNumber, invoiceDate);
                    duplicateReason = "存在相同发票号码和开票日期的记录";
                }
                break;

            case "USER":
                isDuplicate = duplicateCheckRepository.existsByInvoiceNumberAndInvoiceDateAndUserId(invoiceNumber, invoiceDate, userId);
                duplicateReason = "同一用户已提交过相同发票";
                break;

            default:
                isDuplicate = duplicateCheckRepository.existsByInvoiceNumberAndInvoiceDate(invoiceNumber, invoiceDate);
                duplicateReason = "存在相同发票号码和开票日期的记录";
        }

        if (isDuplicate) {
            log.warn("发票查重失败: 发票号码={}, 开票日期={}, 用户ID={}, 原因={}",
                    invoiceNumber, invoiceDate, userId, duplicateReason);
            // 返回重复结果而不是抛出异常
            return DuplicateCheckResult.duplicate(duplicateReason, invoiceNumber, invoiceDate.toString(), userId, duplicateCheckStrategy);
        }

        log.info("发票查重通过: 发票号码={}, 开票日期={}, 用户ID={}", invoiceNumber, invoiceDate, userId);
        return DuplicateCheckResult.notDuplicate(invoiceNumber, invoiceDate.toString(), userId, duplicateCheckStrategy);
    }

    /**
     * 记录发票提交
     */
    @Transactional
    public void recordInvoiceSubmission(InvoiceInfo invoice, String userId) {
        if (!duplicateCheckEnabled) {
            log.info("发票查重功能未启用，跳过记录");
            return;
        }

        if (invoice == null || invoice.getInvoiceNumber() == null || invoice.getInvoiceDate() == null) {
            log.warn("发票信息不完整，无法记录");
            return;
        }

        String invoiceNumber = invoice.getInvoiceNumber().trim();
        LocalDate invoiceDate = parseInvoiceDate(invoice.getInvoiceDate());
        BigDecimal totalAmount = parseTotalAmount(invoice.getTotalAmount());

        if (invoiceDate == null) {
            log.warn("无法解析发票日期，跳过记录: {}", invoice.getInvoiceDate());
            return;
        }

        // 检查是否已存在记录
        Optional<InvoiceDuplicateCheck> existingRecord = duplicateCheckRepository.findByInvoiceNumberAndInvoiceDate(invoiceNumber, invoiceDate);

        if (existingRecord.isPresent()) {
            // 更新现有记录
            InvoiceDuplicateCheck record = existingRecord.get();
            record.setUserId(userId);
            record.setSubmitTime(LocalDateTime.now());
            record.setStatus("SUBMITTED");
            record.setUpdatedTime(LocalDateTime.now());
            duplicateCheckRepository.save(record);
            log.info("更新发票记录: 发票号码={}, 开票日期={}, 用户ID={}", invoiceNumber, invoiceDate, userId);
        } else {
            // 创建新记录
            InvoiceDuplicateCheck record = new InvoiceDuplicateCheck();
            record.setInvoiceNumber(invoiceNumber);
            record.setInvoiceDate(invoiceDate);
            record.setTotalAmount(totalAmount);
            record.setUserId(userId);
            record.setSubmitTime(LocalDateTime.now());
            record.setStatus("SUBMITTED");
            duplicateCheckRepository.save(record);
            log.info("创建发票记录: 发票号码={}, 开票日期={}, 用户ID={}", invoiceNumber, invoiceDate, userId);
        }
    }

    /**
     * 解析发票日期
     */
    private LocalDate parseInvoiceDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        // 尝试多种日期格式
        String[] patterns = {
                "yyyy年MM月dd日", "yyyy-MM-dd", "yyyy/MM/dd",
                "yyyyMMdd", "yyyy.MM.dd"
        };

        for (String pattern : patterns) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                return LocalDate.parse(dateStr.trim(), formatter);
            } catch (DateTimeParseException e) {
                // 继续尝试下一种格式
            }
        }

        // 尝试提取数字日期
        try {
            String cleanDate = dateStr.replaceAll("[^0-9]", "");
            if (cleanDate.length() >= 8) {
                String datePart = cleanDate.substring(0, 8);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
                return LocalDate.parse(datePart, formatter);
            }
        } catch (DateTimeParseException e) {
            log.warn("无法解析发票日期: {}", dateStr);
        }

        return null;
    }

    /**
     * 解析发票金额
     */
    private BigDecimal parseTotalAmount(String amountStr) {
        if (amountStr == null || amountStr.trim().isEmpty()) {
            return null;
        }

        try {
            // 清理金额字符串
            String cleanAmount = amountStr
                    .replace("元", "")
                    .replace("¥", "")
                    .replace("￥", "")
                    .replace("RMB", "")
                    .replace("CNY", "")
                    .replace(",", "")
                    .trim();

            if (cleanAmount.isEmpty()) {
                return null;
            }

            return new BigDecimal(cleanAmount);
        } catch (NumberFormatException e) {
            log.warn("无法解析发票金额: {}", amountStr);
            return null;
        }
    }

    /**
     * 更新发票状态
     */
    @Transactional
    public void updateInvoiceStatus(String invoiceNumber, LocalDate invoiceDate, String status) {
        Optional<InvoiceDuplicateCheck> record = duplicateCheckRepository.findByInvoiceNumberAndInvoiceDate(invoiceNumber, invoiceDate);
        if (record.isPresent()) {
            InvoiceDuplicateCheck invoiceRecord = record.get();
            invoiceRecord.setStatus(status);
            invoiceRecord.setUpdatedTime(LocalDateTime.now());
            duplicateCheckRepository.save(invoiceRecord);
            log.info("更新发票状态: 发票号码={}, 状态={}", invoiceNumber, status);
        }
    }
}