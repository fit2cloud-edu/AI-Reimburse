// #file src/main/java/com/fit2cloud/fapiao/service/ReimbursementService.java
package com.fit2cloud.fapiao.service;

import com.fit2cloud.fapiao.controller.ReimbursementController;
import com.fit2cloud.fapiao.dto.response.InvoiceInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ReimbursementService {

    @Autowired
    private WeComApprovalService weComApprovalService;

    @Autowired
    private BusinessTripService businessTripService;

    @Autowired
    private TravelSubsidyService travelSubsidyService;

    /**
     * 提交报销申请
     */
    public boolean submitReimbursement(ReimbursementController.ReimbursementSubmit submitData, String userId) {
        List<String> approvalIds = new ArrayList<>();
        try {
            // 这里实现报销申请提交逻辑
            List<InvoiceInfo> invoices = submitData.getInvoices();
            String totalAmount = submitData.getTotalAmount();
            String mediaIds = submitData.getMediaIds(); // 获取mediaIds参数
            String formType = submitData.getFormType(); // 获取表单报销类型参数
            String formReimbursementReason = submitData.getFormReimbursementReason(); // 获取报销事由文本参数
            String legalEntity = submitData.getLegalEntity(); // 获取法人实体
            String region = submitData.getRegion(); // 获取区域
            String costDepartment = submitData.getCostDepartment(); // 获取成本部门

            // 客成差旅特有参数
            String customerName = submitData.getCustomerName();
            String unsignedCustomer = submitData.getUnsignedCustomer();
            String travelDays = submitData.getTravelDays();
            String travelStartDate = submitData.getTravelStartDate();
            String travelEndDate = submitData.getTravelEndDate();
            String travelStartPeriod = submitData.getTravelStartPeriod();
            String travelEndPeriod = submitData.getTravelEndPeriod();

            log.info("报销申请提交信息:");
            log.info("总金额: {}", totalAmount);
            log.info("发票数量: {}", invoices != null ? invoices.size() : 0);
            log.info("mediaIds: {}", mediaIds);
            log.info("报销表单类型: {}", formType);
            log.info("报销事由: {}", formReimbursementReason);
            log.info("法人实体: {}", legalEntity);
            log.info("区域: {}", region);
            log.info("成本部门: {}", costDepartment);


            if (invoices != null) {
                for (int i = 0; i < invoices.size(); i++) {
                    InvoiceInfo invoice = invoices.get(i);
                    log.info("发票 {}: {} - {} - {}",
                            i + 1,
                            invoice.getInvoiceItemName(),
                            invoice.getTotalAmount(),
                            invoice.getReimbursementType());
                }
            }

            // 如果是客成差旅报销，先提交出差申请单
            String businessTripApprovalId = null;
            String travelSubsidyApprovalId = null;
            if ("客成差旅报销单".equals(formType)) {
                try {
                    log.info("开始提交出差申请单...");

                    // 使用报销日期作为申请日期
                    String applyDate = submitData.getReimbursementDate();
                    if (applyDate == null) {
                        applyDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    }log.warn("报销日期为空，使用当前日期作为申请日期: {}", applyDate);

// 第一阶段：提交出差申请单
                    log.info("=== 第一阶段：提交出差申请单 ===");
                    businessTripApprovalId = businessTripService.submitBusinessTripApproval(
                            userId, formReimbursementReason, customerName, applyDate, costDepartment,
                            travelStartDate, travelEndDate, travelStartPeriod, travelEndPeriod, travelDays);

                    log.info("出差申请单提交成功，审批编号: {}", businessTripApprovalId);
                    approvalIds.add("出差申请: " + businessTripApprovalId);

                    // 第二阶段：根据前端选择决定是否提交出差补贴申请单
                    Boolean submitTravelSubsidy = submitData.getSubmitTravelSubsidy();
                    if (submitTravelSubsidy == null || submitTravelSubsidy) {
                        log.info("=== 第二阶段：提交出差补贴申请单 ===");
                        travelSubsidyApprovalId = travelSubsidyService.submitTravelSubsidyApproval(
                                userId, submitData.getUserName(), formReimbursementReason, applyDate, costDepartment,
                                travelStartDate, travelEndDate, travelStartPeriod, travelEndPeriod, travelDays, businessTripApprovalId);

                        log.info("出差补贴申请单提交成功，审批编号: {}", travelSubsidyApprovalId);
                        approvalIds.add("出差补贴: " + travelSubsidyApprovalId);
                    } else {
                        log.info("用户选择不提交出差补贴申请单，跳过此步骤");
                    }

                } catch (Exception e) {
                    log.error("提交出差申请单失败，但继续提交报销申请", e);
                    // 这里选择记录错误但继续提交报销申请
                }
            }

            // 第三阶段：分组提交报销申请
            log.info("=== 第三阶段：提交报销申请 ===");
            if (invoices != null && !invoices.isEmpty()) {
                List<List<InvoiceInfo>> invoiceGroups = splitInvoicesBySequence(invoices, 6);
                log.info("将 {} 张发票按顺序分成 {} 组进行提交", invoices.size(), invoiceGroups.size());

                for (int i = 0; i < invoiceGroups.size(); i++) {
                    List<InvoiceInfo> currentGroup = invoiceGroups.get(i);
                  //String groupMediaIds = extractMediaIdsBySequence(mediaIds, invoices, currentGroup);
                    String groupMediaIds = extractMediaIdsByIndex(mediaIds, i, 6, currentGroup.size());
                    log.info("提交第 {} 组报销申请，包含 {} 张发票", i + 1, currentGroup.size());

                    // 计算当前组的金额
                    String groupTotalAmount = calculateGroupTotalAmount(currentGroup);

                    // 提交当前组的报销申请
                    String approvalId = weComApprovalService.submitApproval(
                            userId, currentGroup, groupTotalAmount, groupMediaIds, formType,
                            buildGroupReason(formReimbursementReason, invoiceGroups.size(), i + 1),
                            legalEntity, region, costDepartment, customerName, unsignedCustomer,
                            travelDays, travelStartDate, travelEndDate, travelStartPeriod, travelEndPeriod,
                            businessTripApprovalId);

                    approvalIds.add(approvalId);
                    log.info("第 {} 组报销申请提交成功，审批编号: {}", i + 1, approvalId);
                }
            }

            // 记录出差申请单和报销申请单的关联关系
            if (businessTripApprovalId != null && !approvalIds.isEmpty()) {
                log.info("出差申请单 {} 与报销申请单 {} 关联成功", businessTripApprovalId, approvalIds);
            }

            return true;
        } catch (Exception e) {
            log.error("报销申请提交失败", e);
            return false;
        }
    }

    /**
     * 按索引直接提取mediaIds
     */
    private String extractMediaIdsByIndex(String mediaIds, int groupIndex, int groupSize, int currentGroupSize) {
        if (mediaIds == null || mediaIds.trim().isEmpty()) {
            return "";
        }

        String[] mediaIdArray = mediaIds.split(",");
        List<String> groupMediaIds = new ArrayList<>();

        int startIndex = groupIndex * groupSize;

        for (int i = 0; i < currentGroupSize; i++) {
            int mediaIndex = startIndex + i;
            if (mediaIndex < mediaIdArray.length) {
                groupMediaIds.add(mediaIdArray[mediaIndex].trim());
            } else {
                log.warn("mediaIndex {} 超出mediaIds数组范围，总长度: {}", mediaIndex, mediaIdArray.length);
                break;
            }
        }

        log.info("按索引提取第 {} 组mediaIds: 起始索引={}, 数量={}, 结果={}",
                groupIndex + 1, startIndex, currentGroupSize, groupMediaIds);

        return String.join(",", groupMediaIds);
    }

    /**
     * 将发票列表按顺序分组
     */
    private List<List<InvoiceInfo>> splitInvoicesBySequence(List<InvoiceInfo> invoices, int groupSize) {
        List<List<InvoiceInfo>> groups = new ArrayList<>();

        if (invoices == null || invoices.isEmpty()) {
            return groups;
        }

        log.info("开始按顺序分组发票，总数: {}, 每组大小: {}", invoices.size(), groupSize);

        for (int i = 0; i < invoices.size(); i += groupSize) {
            int end = Math.min(i + groupSize, invoices.size());
            List<InvoiceInfo> group = new ArrayList<>(invoices.subList(i, end));
            groups.add(group);

            log.info("第 {} 组: 包含第 {} 到第 {} 张发票",
                    groups.size(), i + 1, end);
        }

        log.info("分组完成，共 {} 组", groups.size());
        return groups;
    }

    /**
     * 按顺序提取当前组对应的mediaIds
     */
    private String extractMediaIdsBySequence(String mediaIds, List<InvoiceInfo> allInvoices,
                                             List<InvoiceInfo> currentGroup) {
        if (mediaIds == null || mediaIds.trim().isEmpty()) {
            return "";
        }

        String[] mediaIdArray = mediaIds.split(",");
        List<String> groupMediaIds = new ArrayList<>();

        // 找到当前组在原始列表中的位置
        int startIndex = allInvoices.indexOf(currentGroup.get(0));

        for (int i = 0; i < currentGroup.size(); i++) {
            int mediaIndex = startIndex + i;
            if (mediaIndex < mediaIdArray.length) {
                groupMediaIds.add(mediaIdArray[mediaIndex].trim());
            }
        }

        log.info("提取第 {} 到第 {} 张发票的mediaIds: {}",
                startIndex + 1, startIndex + currentGroup.size(), groupMediaIds);

        return String.join(",", groupMediaIds);
    }

    /**
     * 计算当前组的金额总和
     */
    private String calculateGroupTotalAmount(List<InvoiceInfo> invoices) {
        BigDecimal total = BigDecimal.ZERO;

        for (InvoiceInfo invoice : invoices) {
            if (invoice.getTotalAmount() != null) {
                try {
                    String amountStr = invoice.getTotalAmount().replace("元", "").replace(" ", "").trim();
                    BigDecimal amount = new BigDecimal(amountStr);
                    total = total.add(amount);
                } catch (NumberFormatException e) {
                    log.warn("解析发票金额失败: {}", invoice.getTotalAmount());
                }
            }
        }

        String result = total.setScale(2, BigDecimal.ROUND_HALF_UP).toString() + "元";
        log.info("当前组金额计算: {} 张发票，总金额: {}", invoices.size(), result);
        return result;
    }

    /**
     * 构建分组报销事由
     */
    private String buildGroupReason(String originalReason, int totalGroups, int currentGroup) {
        if (totalGroups <= 1) {
            return originalReason;
        }

        String groupReason = originalReason + String.format("（第%d部分，共%d部分）", currentGroup, totalGroups);
        log.info("分组事由构建: {}", groupReason);
        return groupReason;
    }

}