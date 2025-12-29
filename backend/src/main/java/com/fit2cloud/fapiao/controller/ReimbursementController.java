// #file src/main/java/com/fit2cloud/fapiao/controller/ReimbursementController.java
package com.fit2cloud.fapiao.controller;

import com.fit2cloud.fapiao.dto.response.ApiResponse;
import com.fit2cloud.fapiao.dto.response.InvoiceInfo;
import com.fit2cloud.fapiao.service.ReimbursementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/reimbursement")
@Slf4j
public class ReimbursementController {

    private final ReimbursementService reimbursementService;

    public ReimbursementController(ReimbursementService reimbursementService) {
        this.reimbursementService = reimbursementService;
    }

    /**
     * 提交报销申请
     */
    @PostMapping("/submit")
    public ApiResponse<String> submitReimbursement(@RequestBody ReimbursementSubmit submitData,
                                                   @RequestParam(required = false) String userId,
                                                   @RequestParam(required = false) String userName) {
        try {
            log.info("收到报销申请提交, 发票数量: {}, 总金额: {}, 用户ID: {}，用户名: {}",
                    submitData.getInvoices() != null ? submitData.getInvoices().size() : 0,
                    submitData.getTotalAmount(),
                    userId,
                    userName);

            // 如果没有提供userId，使用默认值
            String effectiveUserId = (userId != null && !userId.isEmpty() && !userId.equals("defaultUser"))
                    ? userId : (submitData.getUserId() != null ? submitData.getUserId() : "CiShiWuJian");

            // 验证用户信息完整性
            if (effectiveUserId == null || effectiveUserId.isEmpty() || "default_user".equals(effectiveUserId)) {
                log.error("提交失败：用户ID为空或无效，submitData: {}", submitData);
                return ApiResponse.error("提交人员信息缺失，请先重新登录");
            }

            log.info("使用用户ID: {} 提交报销申请", effectiveUserId);

            boolean success = reimbursementService.submitReimbursement(submitData, effectiveUserId);
            if (success) {
                return ApiResponse.success("报销申请提交成功");
            } else {
                return ApiResponse.error("报销申请提交失败");
            }
        } catch (Exception e) {
            log.error("提交报销申请异常", e);
            return ApiResponse.error("提交失败: " + e.getMessage());
        }
    }

    // 请求参数类
    public static class ReimbursementSubmit {
        private List<InvoiceInfo> invoices;
        private String totalAmount;
        private String mediaIds; // 添加mediaIds字段
        private String formType;//表单报销类型
        private String formReimbursementReason;//新增：报销事由文本
        private String legalEntity; // 法人实体
        private String region; // 区域
        private String costDepartment; // 成本部门
        private String userId;
        private String userName;

        // 新增报销日期字段
        private String reimbursementDate;

        // 客成差旅报销特有字段
        private String customerName;       // 客户名称
        private String unsignedCustomer;   // 未签单客户
        private String travelStartDate;       // 开始日期
        private String travelStartPeriod;     // 开始时段
        private String travelEndDate;         // 结束日期
        private String travelEndPeriod;       // 结束时段
        private String travelDays;            // 出差天数

        // 新增：是否提交出差补贴申请单（默认true）
        private Boolean submitTravelSubsidy = true;

        public List<InvoiceInfo> getInvoices() {
            return invoices;
        }

        public void setInvoices(List<InvoiceInfo> invoices) {
            this.invoices = invoices;
        }

        public String getTotalAmount() {
            return totalAmount;
        }

        public void setTotalAmount(String totalAmount) {
            this.totalAmount = totalAmount;
        }

        public String getMediaIds() {
            return mediaIds;
        } // 添加getter方法

        public void setMediaIds(String mediaIds) {
            this.mediaIds = mediaIds;
        } // 添加setter方法

        public String getFormType() {
            return formType;
        }

        public void setFormType(String formType) {
            this.formType = formType;
        }

        public String getFormReimbursementReason  () {
            return formReimbursementReason;
        }

        public void setFormReimbursementReason(String formReimbursementReason) {
            this.formReimbursementReason = formReimbursementReason;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getLegalEntity() {
            return legalEntity;
        }

        public void setLegalEntity(String legalEntity) {
            this.legalEntity = legalEntity;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getCostDepartment() {
            return costDepartment;
        }

        public void setCostDepartment(String costDepartment) {
            this.costDepartment = costDepartment;
        }

        // 添加getter和setter方法
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }

        public String getUnsignedCustomer() { return unsignedCustomer; }
        public void setUnsignedCustomer(String unsignedCustomer) { this.unsignedCustomer = unsignedCustomer; }

        public String getTravelStartDate() { return travelStartDate; }
        public void setTravelStartDate(String travelStartDate) { this.travelStartDate = travelStartDate; }

        public String getTravelStartPeriod() { return travelStartPeriod; }
        public void setTravelStartPeriod(String travelStartPeriod) { this.travelStartPeriod = travelStartPeriod; }

        public String getTravelEndDate() { return travelEndDate; }
        public void setTravelEndDate(String travelEndDate) { this.travelEndDate = travelEndDate; }

        public String getTravelEndPeriod() { return travelEndPeriod; }
        public void setTravelEndPeriod(String travelEndPeriod) { this.travelEndPeriod = travelEndPeriod; }

        public String getTravelDays() { return travelDays; }
        public void setTravelDays(String travelDays) { this.travelDays = travelDays; }

        // 添加getter和setter方法
        public String getReimbursementDate() { return reimbursementDate; }
        public void setReimbursementDate(String reimbursementDate) { this.reimbursementDate = reimbursementDate; }

        // 是否提交出差补贴申请单的getter和setter
        public Boolean getSubmitTravelSubsidy() {
            return submitTravelSubsidy != null ? submitTravelSubsidy : true; // 默认true
        }

        public void setSubmitTravelSubsidy(Boolean submitTravelSubsidy) {
            this.submitTravelSubsidy = submitTravelSubsidy;
        }

    }

}
