// #file src/main/java/com/fit2cloud/fapiao/service/WeComApprovalService.java
package com.fit2cloud.fapiao.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fit2cloud.fapiao.dto.request.WeComApprovalRequest;
import com.fit2cloud.fapiao.dto.response.InvoiceInfo;
import com.fit2cloud.fapiao.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WeComApprovalService {

    @Value("${qywechat.approval.template-id.daily:}")
    private String dailyTemplateId;

    @Value("${qywechat.approval.template-id.travel:}")
    private String travelTemplateId;

    @Value("${qywechat.approval.template-id.business-trip:}")
    private String businessTripTemplateId;

    @Autowired
    private QyWechatService qyWechatService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private UserDepartmentRelationService userDepartmentRelationService;

    /**
     * 在提交审批前获取用户的部门信息作为默认值
     */
    public Map<String, Object> getUserDepartmentInfo(String userId) {
        log.info("获取用户 {} 的部门信息作为费用承担部门默认值", userId);

        try {
            // 从 UserDepartmentRelationService 获取部门结构
            Map<String, Object> departmentInfo = userDepartmentRelationService.getDepartmentStructureForUser(userId);

            if (departmentInfo.isEmpty()) {
                log.warn("用户 {} 没有找到部门信息，使用默认值", userId);
                return getDefaultDepartmentInfo();
            }

            return departmentInfo;
        } catch (Exception e) {
            log.error("获取用户部门信息失败", e);
            return getDefaultDepartmentInfo();
        }
    }

    /**
     * 获取默认的部门信息
     */
    private Map<String, Object> getDefaultDepartmentInfo() {
        Map<String, Object> defaultInfo = new HashMap<>();
        defaultInfo.put("departmentId", "7"); // 默认智能体开发部门
        defaultInfo.put("departmentName", "智能体开发");
        defaultInfo.put("fullPath", "智能体开发");
        defaultInfo.put("region", "华东区域");
        defaultInfo.put("regionDepartmentId", "3");
        return defaultInfo;
    }


    /**
     * 提交报销申请到企业微信审批
     */
    public String submitApproval(String userId, List<InvoiceInfo> invoices,
                                 String totalAmount, String mediaIds,
                                 String formType, String formReimbursementReason,
                                 String legalEntity, String region, String costDepartment,
                                 String customerName, String unsignedCustomer,
                                 String travelDays, String travelStartDate,
                                 String travelEndDate, String travelStartPeriod,
                                 String travelEndPeriod, String relatedApprovalNo) {
        log.info("==== 调试信息 ==== 用户修改的报销事由: '{}', 法人实体: '{}', 区域: '{}', 成本部门: '{}'",
                formReimbursementReason, legalEntity, region, costDepartment);
        log.info("==== 调试信息 ==== 表单类型: '{}', 客户名称: '{}', 未签单客户: '{}', 出差天数: '{}'",
                formType, customerName, unsignedCustomer, travelDays);
        log.info("==== 调试信息 ==== 表单类型: '{}', 出差时间: {} {} 至 {} {}, 天数: {}天",
                formType, travelStartDate, travelStartPeriod, travelEndDate, travelEndPeriod, travelDays);
        try {
            log.info("开始提交企业微信审批, 申请人: {}, 表单类型: {}, 发票数: {}, 总金额: {}, mediaIds: {}, 表单类型：{}, 法人实体: {}, 区域: {}, 成本部门: {}",
                    userId, formType, invoices.size(), totalAmount, mediaIds, formType, legalEntity, region, costDepartment);

            // 根据表单类型选择模板
            String templateId = getTemplateIdByFormType(formType);
            if (templateId == null) {
                throw new BusinessException("未配置" + formType + "对应的审批模板");
            }

            // 验证输入参数
            if (invoices == null || invoices.isEmpty()) {
                throw new BusinessException("发票信息不能为空");
            }

            if (totalAmount == null || totalAmount.trim().isEmpty()) {
                throw new BusinessException("总金额不能为空");
            }

            // 获取access token
            String accessToken = qyWechatService.getAccessTokenForApproval();

            // 根据报销类型构造不同的审批请求
            WeComApprovalRequest approvalRequest;
            if ("客成差旅报销单".equals(formType)) {
                approvalRequest = buildTravelApprovalRequest(userId, invoices, totalAmount, mediaIds,
                        formReimbursementReason, templateId, legalEntity,
                        region, costDepartment, customerName,
                        unsignedCustomer, travelDays,
                        travelStartDate, travelEndDate, travelStartPeriod, travelEndPeriod, relatedApprovalNo);
            } else {
                // 日常报销使用原有的构建方法
                approvalRequest = buildApprovalRequest(userId, invoices, totalAmount, mediaIds,
                        formType, formReimbursementReason, templateId,
                        legalEntity, region, costDepartment);
            }

            // 调试：打印请求数据
            ObjectMapper mapper = new ObjectMapper();
            String requestJson = mapper.writeValueAsString(approvalRequest);
            log.info("企业微信审批请求数据: {}", requestJson);

            // 发送请求到企业微信
            String url = "https://qyapi.weixin.qq.com/cgi-bin/oa/applyevent?access_token=" + accessToken;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<WeComApprovalRequest> requestEntity = new HttpEntity<>(approvalRequest, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer errcode = (Integer) responseBody.get("errcode");

                if (errcode != null && errcode == 0) {
                    String spNo = (String) responseBody.get("sp_no");
                    log.info("企业微信审批提交成功, 审批编号: {}", spNo);
                    return spNo;
                } else {
                    String errmsg = (String) responseBody.get("errmsg");
                    log.error("企业微信审批提交失败, 错误码: {}, 错误信息: {}", errcode, errmsg);
                    throw new BusinessException("提交审批失败: " + errmsg);
                }
            } else {
                throw new BusinessException("提交审批请求失败: " + response.getStatusCode());
            }
        } catch (BusinessException e) {
            log.error("提交企业微信审批业务异常", e);
            throw e;
        } catch (Exception e) {
            log.error("提交企业微信审批异常", e);
            throw new BusinessException("提交审批异常: " + e.getMessage());
        }
    }

    /**
     * 根据表单类型获取模板ID
     */
    private String getTemplateIdByFormType(String formType) {
        if ("客成差旅报销单".equals(formType)) {
            return travelTemplateId;
        }
        return dailyTemplateId; // 默认为日常报销
    }

    /**
     * 构造企业微信审批请求对象
     */
    private WeComApprovalRequest buildApprovalRequest(String userId, List<InvoiceInfo> invoices, String totalAmount, String mediaIds, String formType, String formReimbursementReason, String templateId, String legalEntity, String region, String costDepartment) {
        // 如果前端没有传入成本部门，使用用户的默认部门
        if (costDepartment == null || costDepartment.trim().isEmpty()) {
            Map<String, Object> userDeptInfo = getUserDepartmentInfo(userId);
            costDepartment = (String) userDeptInfo.get("departmentId");
            log.info("使用用户默认部门作为成本部门: {}", costDepartment);
        }

        // 如果前端没有传入区域，使用用户部门的区域
        if (region == null || region.trim().isEmpty()) {
            Map<String, Object> userDeptInfo = getUserDepartmentInfo(userId);
            region = (String) userDeptInfo.get("region");
            log.info("使用用户默认区域: {}", region);
        }

        WeComApprovalRequest request = new WeComApprovalRequest();

        // 基本信息
        request.setCreator_userid(userId);
        request.setTemplate_id(templateId);
        request.setUse_template_approver(0); // 不使用模板审批人

        // 审批人设置
        List<WeComApprovalRequest.Approver> approvers = new ArrayList<>();

        // 直接上级审批人
        WeComApprovalRequest.Approver approver1 = new WeComApprovalRequest.Approver();
        approver1.setAttr(2); // 2-直接上级
        approver1.setUserid(Arrays.asList(userId));
        approvers.add(approver1);

        // 固定审批人
        WeComApprovalRequest.Approver approver2 = new WeComApprovalRequest.Approver();
        approver2.setAttr(1); // 1-固定审批人
        approver2.setUserid(Arrays.asList("XueQi")); // 固定审批人ID
        approvers.add(approver2);

        request.setApprover(approvers);

        // 通知人设置
        request.setNotifyer(Arrays.asList(userId));
        request.setNotify_type(1); // 1-申请时通知审批人

        // 构造申请数据
        request.setApply_data(buildApplyData(userId, invoices, totalAmount, mediaIds, formType, formReimbursementReason, legalEntity, region, costDepartment));

        // 摘要信息
        request.setSummary_list(buildSummaryList(userId, invoices, totalAmount, legalEntity, region, costDepartment));

        return request;
    }

    private String buildDescriptionValue(String formType, String reason, List<InvoiceInfo> invoices) {
        StringBuilder desc = new StringBuilder();
        desc.append("【").append(formType).append("】");
        desc.append(reason);
        desc.append("（含").append(determineExpenseType(invoices)).append("）");
        return desc.toString();
    }


    /**
     * 构造申请数据
     */
    private WeComApprovalRequest.ApplyData buildApplyData(String userId, List<InvoiceInfo> invoices, String totalAmount, String mediaIds, String formType, String formReimbursementReason, String legalEntity, String region, String costDepartment) {
        WeComApprovalRequest.ApplyData applyData = new WeComApprovalRequest.ApplyData();
        List<WeComApprovalRequest.Content> contents = new ArrayList<>();

        // 报销类型选择器 - 日常报销单
        WeComApprovalRequest.Content typeSelector = new WeComApprovalRequest.Content();
        typeSelector.setControl("Selector");
        typeSelector.setId("item-1503317593875");
        WeComApprovalRequest.SelectorValue typeValue = new WeComApprovalRequest.SelectorValue();
        WeComApprovalRequest.Selector typeSelectorObj = new WeComApprovalRequest.Selector();
        typeSelectorObj.setType("single");
        WeComApprovalRequest.Option typeOption = new WeComApprovalRequest.Option();

        // 根据表单类型设置key和文本
        String typeKey, typeText;
        if ("客成差旅报销单".equals(formType)) {
            typeKey = "option-1761552772993"; // 客成差旅报销单对应的key（需在模板中确认）
            typeText = "客成差旅报销单";
        } else {
            typeKey = "option-127158624"; // 日常报销单
            typeText = "日常报销单";
        }
        typeOption.setKey(typeKey);

        WeComApprovalRequest.TextValue typeTextValue = new WeComApprovalRequest.TextValue();
        typeTextValue.setText(typeText);
        typeTextValue.setLang("zh_CN");
        typeOption.setValue(Arrays.asList(typeTextValue));
        typeSelectorObj.setOptions(Arrays.asList(typeOption));
        typeValue.setSelector(typeSelectorObj);
        typeSelector.setValue(typeValue);
        contents.add(typeSelector);

        // 公司选择器
        WeComApprovalRequest.Content companySelector = new WeComApprovalRequest.Content();
        companySelector.setControl("Selector");
        companySelector.setId("Selector-1761551540216");
        WeComApprovalRequest.SelectorValue companyValue = new WeComApprovalRequest.SelectorValue();
        WeComApprovalRequest.Selector companySelectorObj = new WeComApprovalRequest.Selector();
        companySelectorObj.setType("single");
        WeComApprovalRequest.Option companyOption = new WeComApprovalRequest.Option();
        companyOption.setKey("option-1761551540216");
        WeComApprovalRequest.TextValue companyText = new WeComApprovalRequest.TextValue();
        companyText.setText(legalEntity != null ? legalEntity : "杭州飞致云信息科技有限公司（CODE1）");
        companyText.setLang("zh_CN");
        companyOption.setValue(Arrays.asList(companyText));
        companySelectorObj.setOptions(Arrays.asList(companyOption));
        companyValue.setSelector(companySelectorObj);
        companySelector.setValue(companyValue);
        contents.add(companySelector);

        // 联系人控件
        WeComApprovalRequest.Content contact = new WeComApprovalRequest.Content();
        contact.setControl("Contact");
        contact.setId("Contact-1761551660569");
        WeComApprovalRequest.ContactValue contactValue = new WeComApprovalRequest.ContactValue();
        WeComApprovalRequest.Member member = new WeComApprovalRequest.Member();
        member.setUserid(userId);
        // 使用实际用户姓名
        String actualUserName = getActualUserName(userId); // 获取实际用户姓名
        member.setName(actualUserName);
        // 部门模式的Contact控件不需要设置partyid
        member.setPartyid(null);
        contactValue.setMembers(Arrays.asList(member));
        contact.setValue(contactValue);
        contents.add(contact);

        // 日期控件
        WeComApprovalRequest.Content date = new WeComApprovalRequest.Content();
        date.setControl("Date");
        date.setId("Date-1761551710747");
        WeComApprovalRequest.DateValue dateValue = new WeComApprovalRequest.DateValue();
        WeComApprovalRequest.Date dateObj = new WeComApprovalRequest.Date();
        dateObj.setType("day");
        dateObj.setS_timestamp(String.valueOf(System.currentTimeMillis() / 1000));
        dateValue.setDate(dateObj);
        date.setValue(dateValue);
        contents.add(date);

        // 区域控件（Contact-1763103056039）- 部门模式的Contact控件，用于选择区域
        WeComApprovalRequest.Content regionContact = new WeComApprovalRequest.Content();
        regionContact.setControl("Contact");
        regionContact.setId("Contact-1763103056039");
        WeComApprovalRequest.ContactValue regionValue = new WeComApprovalRequest.ContactValue();

        // 根据区域名称找到对应的区域部门ID
        String regionDepartmentId = findRegionDepartmentId(region);
        if (regionDepartmentId != null && !regionDepartmentId.isEmpty()) {
            WeComApprovalRequest.Member regionMember = new WeComApprovalRequest.Member();
            // 对于部门模式的Contact控件，userid 设置为空字符串
            // regionMember.setUserid("");  // 空字符串
            // 设置部门ID列表
            regionMember.setPartyid(Arrays.asList(regionDepartmentId));
            // 必须设置部门名称，否则详情中显示为空
            String regionDeptName = getDepartmentNameById(regionDepartmentId);
            regionMember.setName(regionDeptName);
            regionValue.setMembers(Arrays.asList(regionMember));
            log.info("设置区域控件，区域: {}, 部门ID: {}, 部门名称: {}", region, regionDepartmentId, regionDeptName);
        } else {
            regionValue.setMembers(new ArrayList<>());
            log.warn("未找到区域对应的部门ID: {}", region);
        }
        regionContact.setValue(regionValue);
        contents.add(regionContact);

        // 费用承担部门控件
        WeComApprovalRequest.Content costDeptContact = new WeComApprovalRequest.Content();
        costDeptContact.setControl("Contact");
        costDeptContact.setId("Contact-1763102909388");
        WeComApprovalRequest.ContactValue costDeptValue = new WeComApprovalRequest.ContactValue();

        if (costDepartment != null && !costDepartment.trim().isEmpty()) {
            try {
                // 验证部门ID格式
                String deptId = costDepartment.trim();
                if (isValidDepartmentId(deptId)) {
                    WeComApprovalRequest.Member costDeptMember = new WeComApprovalRequest.Member();

                    // 对于部门模式的Contact控件，userid 设置为空字符串
                    // costDeptMember.setUserid("");  // 空字符串
                    // 设置部门ID列表
                    costDeptMember.setPartyid(Arrays.asList(deptId));
                    // 必须设置部门名称
                    String deptName = getDepartmentNameById(deptId);
                    costDeptMember.setName(deptName);

                    costDeptValue.setMembers(Arrays.asList(costDeptMember));

                    log.info("设置费用承担部门，部门ID: {}", deptId, deptName);
                } else {
                    log.warn("无效的部门ID格式: {}", costDepartment);
                    costDeptValue.setMembers(new ArrayList<>());
                }
            } catch (Exception e) {
                log.error("处理部门ID异常: {}", costDepartment, e);
                costDeptValue.setMembers(new ArrayList<>());
            }
        } else {
            log.warn("费用承担部门为空");
            costDeptValue.setMembers(new ArrayList<>());
        }
        costDeptContact.setValue(costDeptValue);
        contents.add(costDeptContact);

        // 描述文本框
        WeComApprovalRequest.Content description = new WeComApprovalRequest.Content();
        description.setControl("Textarea");
        description.setId("Textarea-1761552419959");
        WeComApprovalRequest.TextareaValue descValue = new WeComApprovalRequest.TextareaValue();

        // 使用用户输入的报销事由，如果没有输入则使用默认文本
        String finalReasonText = (formReimbursementReason != null && !formReimbursementReason.trim().isEmpty()) ? formReimbursementReason : "发票报销申请（" + determineExpenseType(invoices) + "）";
        descValue.setText(finalReasonText);
        description.setValue(descValue);
        contents.add(description);

        // 表格控件（发票详情）
        WeComApprovalRequest.Content table = new WeComApprovalRequest.Content();
        table.setControl("Table");
        table.setId("item-1503317853434");
        WeComApprovalRequest.TableValue tableValue = new WeComApprovalRequest.TableValue();
        tableValue.setChildren(buildInvoiceTableData(invoices, mediaIds));
        table.setValue(tableValue);
        contents.add(table);

        applyData.setContents(contents);
        return applyData;
    }



    /**
     * 构造客成差旅报销审批请求
     */
    private WeComApprovalRequest buildTravelApprovalRequest(String userId, List<InvoiceInfo> invoices,
                                                            String totalAmount, String mediaIds,
                                                            String formReimbursementReason, String templateId,
                                                            String legalEntity, String region, String costDepartment,
                                                            String customerName, String unsignedCustomer,
                                                            String travelDays, String travelStartDate,
                                                            String travelEndDate, String travelStartPeriod,
                                                            String travelEndPeriod, String relatedApprovalNo) {
        WeComApprovalRequest request = new WeComApprovalRequest();

        // 基本信息
        request.setCreator_userid(userId);
        request.setTemplate_id(templateId);
        request.setUse_template_approver(0);

        // 审批人设置 - 可以根据需要调整
        List<WeComApprovalRequest.Approver> approvers = new ArrayList<>();

        WeComApprovalRequest.Approver approver1 = new WeComApprovalRequest.Approver();
        approver1.setAttr(2); // 直接上级
        approver1.setUserid(Arrays.asList(userId));
        approvers.add(approver1);

        WeComApprovalRequest.Approver approver2 = new WeComApprovalRequest.Approver();
        approver2.setAttr(1); // 固定审批人
        approver2.setUserid(Arrays.asList("XueQi")); // 固定审批人ID
        approvers.add(approver2);

        request.setApprover(approvers);
        request.setNotifyer(Arrays.asList(userId));
        request.setNotify_type(1);

        // 构造申请数据 - 使用客成差旅特有的字段
        request.setApply_data(buildTravelApplyData(userId, invoices, totalAmount, mediaIds,
                formReimbursementReason, legalEntity, region,
                costDepartment, customerName, unsignedCustomer, travelDays, travelStartDate, travelEndDate,
                travelStartPeriod, travelEndPeriod, relatedApprovalNo));

        // 摘要信息
        request.setSummary_list(buildTravelSummaryList(userId, invoices, totalAmount, legalEntity,
                region, costDepartment, customerName, travelDays, travelStartDate, travelEndDate,
                travelStartPeriod, travelEndPeriod));

        return request;
    }

    /**
     * 构造客成差旅报销申请数据
     */
    private WeComApprovalRequest.ApplyData buildTravelApplyData(String userId, List<InvoiceInfo> invoices,
                                                                String totalAmount, String mediaIds,
                                                                String formReimbursementReason, String legalEntity,
                                                                String region, String costDepartment,
                                                                String customerName, String unsignedCustomer,
                                                                String travelDays, String travelStartDate,
                                                                String travelEndDate, String travelStartPeriod,
                                                                String travelEndPeriod, String relatedApprovalNo) {
        WeComApprovalRequest.ApplyData applyData = new WeComApprovalRequest.ApplyData();
        List<WeComApprovalRequest.Content> contents = new ArrayList<>();

        // 1. 报销类型选择器 - 固定为客成差旅报销
        WeComApprovalRequest.Content typeSelector = new WeComApprovalRequest.Content();
        typeSelector.setControl("Selector");
        typeSelector.setId("item-1503317593875");
        WeComApprovalRequest.SelectorValue typeValue = new WeComApprovalRequest.SelectorValue();
        WeComApprovalRequest.Selector typeSelectorObj = new WeComApprovalRequest.Selector();
        typeSelectorObj.setType("single");
        WeComApprovalRequest.Option typeOption = new WeComApprovalRequest.Option();
        typeOption.setKey("option-127158625"); // 客成差旅报销的key
        WeComApprovalRequest.TextValue typeTextValue = new WeComApprovalRequest.TextValue();
        typeTextValue.setText("客成差旅报销");
        typeTextValue.setLang("zh_CN");
        typeOption.setValue(Arrays.asList(typeTextValue));
        typeSelectorObj.setOptions(Arrays.asList(typeOption));
        typeValue.setSelector(typeSelectorObj);
        typeSelector.setValue(typeValue);
        contents.add(typeSelector);

        // 2. 法人实体选择器
        WeComApprovalRequest.Content companySelector = new WeComApprovalRequest.Content();
        companySelector.setControl("Selector");
        companySelector.setId("Selector-1763545496437");
        WeComApprovalRequest.SelectorValue companyValue = new WeComApprovalRequest.SelectorValue();
        WeComApprovalRequest.Selector companySelectorObj = new WeComApprovalRequest.Selector();
        companySelectorObj.setType("single");
        WeComApprovalRequest.Option companyOption = new WeComApprovalRequest.Option();
        companyOption.setKey("option-1763545496437");
        WeComApprovalRequest.TextValue companyText = new WeComApprovalRequest.TextValue();
        companyText.setText(legalEntity != null ? legalEntity : "杭州飞致云信息科技有限公司（CODE1）");
        companyText.setLang("zh_CN");
        companyOption.setValue(Arrays.asList(companyText));
        companySelectorObj.setOptions(Arrays.asList(companyOption));
        companyValue.setSelector(companySelectorObj);
        companySelector.setValue(companyValue);
        contents.add(companySelector);

        // 3. 提交人员控件
        WeComApprovalRequest.Content contact = new WeComApprovalRequest.Content();
        contact.setControl("Contact");
        contact.setId("Contact-1761701492179");
        WeComApprovalRequest.ContactValue contactValue = new WeComApprovalRequest.ContactValue();
        WeComApprovalRequest.Member member = new WeComApprovalRequest.Member();
        member.setUserid(userId);
        // 需要获取实际提交人的姓名，这里需要调用企业微信API获取用户信息
        String actualUserName = getActualUserName(userId);
        member.setName(actualUserName);
        contactValue.setMembers(Arrays.asList(member));
        contact.setValue(contactValue);
        contents.add(contact);

        // 4. 报销日期控件
        WeComApprovalRequest.Content date = new WeComApprovalRequest.Content();
        date.setControl("Date");
        date.setId("Date-1761701514091");
        WeComApprovalRequest.DateValue dateValue = new WeComApprovalRequest.DateValue();
        WeComApprovalRequest.Date dateObj = new WeComApprovalRequest.Date();
        dateObj.setType("day");
        dateObj.setS_timestamp(String.valueOf(System.currentTimeMillis() / 1000));
        dateValue.setDate(dateObj);
        date.setValue(dateValue);
        contents.add(date);

        // 5. 区域控件
        WeComApprovalRequest.Content regionContact = new WeComApprovalRequest.Content();
        regionContact.setControl("Contact");
        regionContact.setId("Contact-1763364650385");
        WeComApprovalRequest.ContactValue regionValue = new WeComApprovalRequest.ContactValue();

        String regionDepartmentId = findRegionDepartmentId(region);
        if (regionDepartmentId != null && !regionDepartmentId.isEmpty()) {
            WeComApprovalRequest.Member regionMember = new WeComApprovalRequest.Member();
            regionMember.setPartyid(Arrays.asList(regionDepartmentId));
            String regionDeptName = getDepartmentNameById(regionDepartmentId);
            regionMember.setName(regionDeptName);
            regionValue.setMembers(Arrays.asList(regionMember));
        } else {
            regionValue.setMembers(new ArrayList<>());
        }
        regionContact.setValue(regionValue);
        contents.add(regionContact);

        // 6. 费用承担部门控件
        WeComApprovalRequest.Content costDeptContact = new WeComApprovalRequest.Content();
        costDeptContact.setControl("Contact");
        costDeptContact.setId("Contact-1761701600060");
        WeComApprovalRequest.ContactValue costDeptValue = new WeComApprovalRequest.ContactValue();

        if (costDepartment != null && !costDepartment.trim().isEmpty() && isValidDepartmentId(costDepartment)) {
            WeComApprovalRequest.Member costDeptMember = new WeComApprovalRequest.Member();
            costDeptMember.setPartyid(Arrays.asList(costDepartment.trim()));
            String deptName = getDepartmentNameById(costDepartment);
            costDeptMember.setName(deptName);
            costDeptValue.setMembers(Arrays.asList(costDeptMember));
        } else {
            costDeptValue.setMembers(new ArrayList<>());
        }
        costDeptContact.setValue(costDeptValue);
        contents.add(costDeptContact);

        // 7. 客户名称文本框 - 客成差旅特有字段
        if (customerName != null && !customerName.trim().isEmpty()) {
            WeComApprovalRequest.Content customerNameInput = new WeComApprovalRequest.Content();
            customerNameInput.setControl("Text");
            customerNameInput.setId("Text-1763536985625");
            WeComApprovalRequest.TextareaValue customerValue = new WeComApprovalRequest.TextareaValue();
            customerValue.setText(customerName.trim());
            customerNameInput.setValue(customerValue);
            contents.add(customerNameInput);
        }

        // 8. 未签单客户文本框 - 客成差旅特有字段
        if (unsignedCustomer != null && !unsignedCustomer.trim().isEmpty()) {
            WeComApprovalRequest.Content unsignedCustomerInput = new WeComApprovalRequest.Content();
            unsignedCustomerInput.setControl("Text");
            unsignedCustomerInput.setId("Text-1761702925585");
            WeComApprovalRequest.TextareaValue unsignedCustomerValue = new WeComApprovalRequest.TextareaValue();
            unsignedCustomerValue.setText(unsignedCustomer.trim());
            unsignedCustomerInput.setValue(unsignedCustomerValue);
            contents.add(unsignedCustomerInput);
        }

        // 9. 关联申请单控件 - 新增
        if (relatedApprovalNo != null && !relatedApprovalNo.trim().isEmpty()) {
            WeComApprovalRequest.Content relatedApprovalContent = new WeComApprovalRequest.Content();
            relatedApprovalContent.setControl("RelatedApproval");
            relatedApprovalContent.setId("RelatedApproval-1763534776945");

            WeComApprovalRequest.RelatedApprovalValue relatedApprovalValue = new WeComApprovalRequest.RelatedApprovalValue();
            WeComApprovalRequest.RelatedApproval relatedApproval = new WeComApprovalRequest.RelatedApproval();
            relatedApproval.setSp_no(relatedApprovalNo.trim());
            relatedApprovalValue.setRelated_approval(Arrays.asList(relatedApproval));

            relatedApprovalContent.setValue(relatedApprovalValue);
            contents.add(relatedApprovalContent);

            log.info("设置关联审批单，审批编号: {}", relatedApprovalNo);
        }

        // 10. 出差天数日期范围 - 客成差旅特有字段
        if (travelStartDate != null && !travelStartDate.trim().isEmpty() &&
                travelEndDate != null && !travelEndDate.trim().isEmpty()) {

            WeComApprovalRequest.Content travelDaysRange = new WeComApprovalRequest.Content();
            travelDaysRange.setControl("DateRange");
            travelDaysRange.setId("DateRange-1761702971122");

            // 创建 DateRangeValue 对象
            WeComApprovalRequest.DateRangeValue dateRangeValue = new WeComApprovalRequest.DateRangeValue();
            // 创建 DateRange 对象并设置属性
            WeComApprovalRequest.DateRange dateRange = new WeComApprovalRequest.DateRange();
            dateRange.setType("halfday"); // 设置为半天类型，支持上午/下午

            // 转换日期时间为时间戳
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

                LocalDate startDate = LocalDate.parse(travelStartDate, formatter);
                LocalDate endDate = LocalDate.parse(travelEndDate, formatter);

                // 根据官方文档，halfday 类型的时间戳只能是 00:00:00 或 12:00:00
                long startTimestamp, endTimestamp;

                // 开始时间：如果是上午就是 00:00:00，下午就是 12:00:00
                if ("上午".equals(travelStartPeriod)) {
                    startTimestamp = startDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
                } else {
                    startTimestamp = startDate.atTime(12, 0, 0).atZone(ZoneId.systemDefault()).toEpochSecond();
                }

                // 结束时间：如果是上午就是 00:00:00，下午就是 12:00:00
                if ("上午".equals(travelEndPeriod)) {
                    endTimestamp = endDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
                } else {
                    endTimestamp = endDate.atTime(12, 0, 0).atZone(ZoneId.systemDefault()).toEpochSecond();
                }

                // 计算时长（秒）
                long duration = endTimestamp - startTimestamp;

                // 使用官方文档要求的参数名
                dateRange.setNew_begin(String.valueOf(startTimestamp));
                dateRange.setNew_end(String.valueOf(endTimestamp));
                dateRange.setNew_duration(duration);

                log.info("设置出差日期范围: {} {} 至 {} {}, new_begin: {}, new_end: {}, new_duration: {}秒",
                        travelStartDate, travelStartPeriod, travelEndDate, travelEndPeriod,
                        startTimestamp, endTimestamp, duration);

            } catch (Exception e) {
                log.warn("日期转换失败，使用当前时间作为默认值", e);
                // 使用当前时间作为默认值
                long currentTime = System.currentTimeMillis() / 1000;
                dateRange.setNew_begin(String.valueOf(currentTime - 43200)); // 12小时前
                dateRange.setNew_end(String.valueOf(currentTime)); // 当前时间
                dateRange.setNew_duration(43200L); // 12小时
            }

            // 设置其他必要属性
            dateRangeValue.setDate_range(dateRange);
            travelDaysRange.setValue(dateRangeValue);
            contents.add(travelDaysRange);
        }else {
            log.warn("出差日期信息不完整，跳过设置DateRange控件");
        }

        // 11. 报销事由文本框 - （客成差旅专用）
        WeComApprovalRequest.Content travelReasonInput = new WeComApprovalRequest.Content();
        travelReasonInput.setControl("Textarea");
        travelReasonInput.setId("Textarea-1763710203925"); // 使用模板中的控件ID
        WeComApprovalRequest.TextareaValue travelReasonValue = new WeComApprovalRequest.TextareaValue();

        // 使用用户输入的报销事由，如果没有输入则使用默认文本
        String finalTravelReason = (formReimbursementReason != null && !formReimbursementReason.trim().isEmpty())
                ? formReimbursementReason
                : "差旅费用报销（" + determineExpenseType(invoices) + "）";

        travelReasonValue.setText(finalTravelReason);
        travelReasonInput.setValue(travelReasonValue);
        contents.add(travelReasonInput);

        // 12. 表格控件（报销明细）- 使用客成差旅特有的费用类型
        WeComApprovalRequest.Content table = new WeComApprovalRequest.Content();
        table.setControl("Table");
        table.setId("item-1503317853434");
        WeComApprovalRequest.TableValue tableValue = new WeComApprovalRequest.TableValue();
        tableValue.setChildren(buildTravelInvoiceTableData(invoices, mediaIds));
        table.setValue(tableValue);
        contents.add(table);

        applyData.setContents(contents);
        return applyData;
    }

    /**
     * 构造客成差旅报销发票表格数据
     */
    private List<WeComApprovalRequest.Child> buildTravelInvoiceTableData(List<InvoiceInfo> invoices, String mediaIds) {
        List<WeComApprovalRequest.Child> children = new ArrayList<>();
        String[] fileIdArray = parseMediaIds(mediaIds, invoices.size());

        // 添加调试日志
        log.info("客成差旅报销费用类型映射检查:");
        for (int i = 0; i < invoices.size(); i++) {
            InvoiceInfo invoice = invoices.get(i);
            String originalType = invoice.getReimbursementType();
            String mappedKey = mapTravelExpenseTypeToKey(originalType, invoice.getSubReimbursementType());
            String displayText = buildTravelExpenseTypeDisplayText(originalType, invoice.getSubReimbursementType());
            log.info("发票 {}: 原始类型={}, 映射Key={}, 显示文本={}",
                    i + 1, originalType, mappedKey, displayText);
        }

        for (int i = 0; i < invoices.size(); i++) {
            InvoiceInfo invoice = invoices.get(i);
            String fileId = i < fileIdArray.length ? fileIdArray[i] : null;

            WeComApprovalRequest.Child child = new WeComApprovalRequest.Child();
            List<WeComApprovalRequest.ListItem> listItems = new ArrayList<>();

            // 1. 费用类型选择器
            WeComApprovalRequest.ListItem typeItem = new WeComApprovalRequest.ListItem();
            typeItem.setControl("Selector");
            typeItem.setId("Selector-1761703042707");
            WeComApprovalRequest.SelectorValue typeValue = new WeComApprovalRequest.SelectorValue();
            WeComApprovalRequest.Selector selector = new WeComApprovalRequest.Selector();
            selector.setType("single");
            WeComApprovalRequest.Option option = new WeComApprovalRequest.Option();

            // 使用客成差旅专用的费用类型映射方法
            String expenseTypeKey = mapTravelExpenseTypeToKey(invoice.getReimbursementType(), invoice.getSubReimbursementType());
            option.setKey(expenseTypeKey);

            WeComApprovalRequest.TextValue textValue = new WeComApprovalRequest.TextValue();

            String displayText = buildExpenseTypeDisplayText(invoice.getReimbursementType(), invoice.getSubReimbursementType());
            textValue.setText(displayText);
            textValue.setLang("zh_CN");
            option.setValue(Arrays.asList(textValue));
            selector.setOptions(Arrays.asList(option));
            typeValue.setSelector(selector);
            typeItem.setValue(typeValue);
            listItems.add(typeItem);

            // 2. 金额控件
            WeComApprovalRequest.ListItem moneyItem = new WeComApprovalRequest.ListItem();
            moneyItem.setControl("Money");
            moneyItem.setId("item-1503317989302");
            WeComApprovalRequest.MoneyValue moneyValue = new WeComApprovalRequest.MoneyValue();
            String amountStr = invoice.getTotalAmount();
            if (amountStr != null) {
                amountStr = amountStr.replace("元", "").replace(" ", "").trim();
            }
            moneyValue.setNew_money(amountStr);
            moneyItem.setValue(moneyValue);
            listItems.add(moneyItem);

            // 3. 日期控件
            WeComApprovalRequest.ListItem dateItem = new WeComApprovalRequest.ListItem();
            dateItem.setControl("Date");
            dateItem.setId("Date-1761703096139");
            WeComApprovalRequest.DateValue dateValue = new WeComApprovalRequest.DateValue();
            WeComApprovalRequest.Date date = new WeComApprovalRequest.Date();
            date.setType("day");
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            if (invoice.getInvoiceDate() != null && !invoice.getInvoiceDate().isEmpty()) {
                try {
                    LocalDate invoiceDate = LocalDate.parse(invoice.getInvoiceDate());
                    timestamp = String.valueOf(invoiceDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond());
                } catch (Exception e) {
                    log.warn("解析发票日期失败，使用当前时间: {}", invoice.getInvoiceDate());
                }
            }
            date.setS_timestamp(timestamp);
            dateValue.setDate(date);
            dateItem.setValue(dateValue);
            listItems.add(dateItem);

            // 4. 文件控件（发票）
            WeComApprovalRequest.ListItem fileItem = new WeComApprovalRequest.ListItem();
            fileItem.setControl("File");
            fileItem.setId("File-1761703116355");
            WeComApprovalRequest.FileValue fileValue = new WeComApprovalRequest.FileValue();
            List<WeComApprovalRequest.File> files = new ArrayList<>();
            if (fileId != null && !fileId.trim().isEmpty()) {
                WeComApprovalRequest.File file = new WeComApprovalRequest.File();
                file.setFile_id(fileId.trim());
                files.add(file);
            }
            fileValue.setFiles(files);
            fileItem.setValue(fileValue);
            listItems.add(fileItem);

            // 5. 其他说明文本框
            WeComApprovalRequest.ListItem descItem = new WeComApprovalRequest.ListItem();
            descItem.setControl("Textarea");
            descItem.setId("item-1503318001306");
            WeComApprovalRequest.TextareaValue descValue = new WeComApprovalRequest.TextareaValue();
            String descText = "发票号码：" + (invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "") +
                    "，销售方：" + (invoice.getSellerName() != null ? invoice.getSellerName() : "") +
                    (invoice.getRemark() != null && !invoice.getRemark().isEmpty() ? "，备注：" + invoice.getRemark() : "");
            descValue.setText(descText);
            descItem.setValue(descValue);
            listItems.add(descItem);

            // 6. 消费事由文本框
            WeComApprovalRequest.ListItem expenseDescItem = new WeComApprovalRequest.ListItem();
            expenseDescItem.setControl("Textarea");
            expenseDescItem.setId("Textarea-1761703185541");
            WeComApprovalRequest.TextareaValue expenseDescValue = new WeComApprovalRequest.TextareaValue();
            String expenseDesc = invoice.getConsumptionReason() != null ? invoice.getConsumptionReason() :
                    (invoice.getReimbursementReason() != null ? invoice.getReimbursementReason() : "差旅费用报销");
            expenseDescValue.setText(expenseDesc);
            expenseDescItem.setValue(expenseDescValue);
            listItems.add(expenseDescItem);

            // 7. 附件文件控件
            WeComApprovalRequest.ListItem attachmentFileItem = new WeComApprovalRequest.ListItem();
            attachmentFileItem.setControl("File");
            attachmentFileItem.setId("item-1503385054053");
            WeComApprovalRequest.FileValue attachmentFileValue = new WeComApprovalRequest.FileValue();
            attachmentFileValue.setFiles(new ArrayList<>());
            attachmentFileItem.setValue(attachmentFileValue);
            listItems.add(attachmentFileItem);

            child.setList(listItems);
            children.add(child);
        }

        return children;
    }

    /**
     * 构造客成差旅报销摘要信息
     */
    private List<WeComApprovalRequest.Summary> buildTravelSummaryList(String userId, List<InvoiceInfo> invoices,
                                                                      String totalAmount, String legalEntity,
                                                                      String region, String costDepartment,
                                                                      String customerName, String travelDays,
                                                                      String travelStartDate, String travelEndDate,
                                                                      String travelStartPeriod, String travelEndPeriod) {
        List<WeComApprovalRequest.Summary> summaries = new ArrayList<>();

        // 第一行摘要：报销类型、申请人、客户信息（合并为一行）
        WeComApprovalRequest.Summary summary1 = new WeComApprovalRequest.Summary();
        List<WeComApprovalRequest.SummaryInfo> summaryInfo1 = new ArrayList<>();
        WeComApprovalRequest.SummaryInfo info1 = new WeComApprovalRequest.SummaryInfo();

        String customerInfo = customerName != null && !customerName.trim().isEmpty() ?
                "客户：" + customerName : "客户：未填写";

        String travelInfo = "";
        if (travelStartDate != null && !travelStartDate.trim().isEmpty() &&
                travelEndDate != null && !travelEndDate.trim().isEmpty()) {
            travelInfo = "，出差时间：" + travelStartDate + "至" + travelEndDate;
        }

        // 修正天数计算 - 与DateRange控件保持一致
        String actualTravelDays = calculateActualTravelDays(travelStartDate, travelEndDate, travelStartPeriod, travelEndPeriod);
//        String actualTravelDays = travelDays; // 直接使用前端计算的天数
        if (actualTravelDays != null && !actualTravelDays.isEmpty()) {
            travelInfo += "，出差天数：" + actualTravelDays + "天";
        }

        // 使用实际用户姓名
        String actualUserName = getActualUserName(userId); // 获取实际用户姓名
        info1.setText("客成差旅报销申请 - " + actualUserName + "，" + customerInfo + travelInfo);
        info1.setLang("zh_CN");
        summaryInfo1.add(info1);
        summary1.setSummary_info(summaryInfo1);
        summaries.add(summary1);

        // 第二行摘要：法人实体和部门信息
        WeComApprovalRequest.Summary summary2 = new WeComApprovalRequest.Summary();
        List<WeComApprovalRequest.SummaryInfo> summaryInfo2 = new ArrayList<>();
        WeComApprovalRequest.SummaryInfo info2 = new WeComApprovalRequest.SummaryInfo();

        String costDeptName = getDepartmentNameById(costDepartment);
        String regionDeptName = getDepartmentNameById(findRegionDepartmentId(region));

        String entityInfo = "法人实体：" + (legalEntity != null ? legalEntity : "杭州飞致云信息科技有限公司（CODE1）") +
                "，区域：" + (regionDeptName != null ? regionDeptName : "未选择") +
                "，成本部门：" + (costDeptName != null ? costDeptName : "未选择");

        info2.setText(entityInfo);
        info2.setLang("zh_CN");
        summaryInfo2.add(info2);
        summary2.setSummary_info(summaryInfo2);
        summaries.add(summary2);

        // 第三行摘要：总金额和发票数量
        WeComApprovalRequest.Summary summary3 = new WeComApprovalRequest.Summary();
        List<WeComApprovalRequest.SummaryInfo> summaryInfo3 = new ArrayList<>();
        WeComApprovalRequest.SummaryInfo info3 = new WeComApprovalRequest.SummaryInfo();

        String displayTotalAmount = totalAmount;
        if (displayTotalAmount != null && !displayTotalAmount.isEmpty()) {
            displayTotalAmount = displayTotalAmount.replace("元", "").trim();
            if (!displayTotalAmount.isEmpty()) {
                try {
                    new BigDecimal(displayTotalAmount);
                    displayTotalAmount += "元";
                } catch (NumberFormatException e) {
                    displayTotalAmount = totalAmount;
                }
            }
        }

        info3.setText("总金额：" + displayTotalAmount + "，发票数量：" + invoices.size() + "张");
        info3.setLang("zh_CN");
        summaryInfo3.add(info3);
        summary3.setSummary_info(summaryInfo3);
        summaries.add(summary3);

        return summaries;
    }

    /**
     * 计算实际的出差天数（根据企业微信官方半天规则）
     */
    private String calculateActualTravelDays(String travelStartDate, String travelEndDate,
                                             String travelStartPeriod, String travelEndPeriod) {
        if (travelStartDate == null || travelEndDate == null) {
            return "0";
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            ZoneId zoneId = ZoneId.systemDefault();

            LocalDate startDate = LocalDate.parse(travelStartDate, formatter);
            LocalDate endDate = LocalDate.parse(travelEndDate, formatter);

            long startTimestamp, endTimestamp;

            // 计算时间戳（与提交企业微信的逻辑保持一致）
            if ("上午".equals(travelStartPeriod)) {
                startTimestamp = startDate.atStartOfDay(zoneId).toEpochSecond();
            } else {
                startTimestamp = startDate.atTime(12, 0, 0).atZone(zoneId).toEpochSecond();
            }

            if ("上午".equals(travelEndPeriod)) {
                endTimestamp = endDate.atStartOfDay(zoneId).toEpochSecond();
            } else {
                endTimestamp = endDate.atTime(12, 0, 0).atZone(zoneId).toEpochSecond();
            }

            // 计算时长（秒）并转换为天数
            long durationSeconds = endTimestamp - startTimestamp;
            double actualDays = (double) durationSeconds / (24 * 3600);

            // 格式化输出
            String result;
            if (actualDays == (int) actualDays) {
                result = String.valueOf((int) actualDays);
            } else {
                result = String.valueOf(actualDays);
            }

            log.info("实际出差天数计算结果: {}天 (duration: {}秒)", result, durationSeconds);
            return result;

        } catch (Exception e) {
            log.warn("计算实际出差天数失败", e);
            return "0";
        }
    }

    /**
     * 根据部门ID获取部门名称
     */
    private String getDepartmentNameById(String departmentId) {
        if (departmentId == null || departmentId.trim().isEmpty()) {
            return "";
        }

        try {
            List<Map<String, Object>> departments = departmentService.getDepartmentList();
            for (Map<String, Object> dept : departments) {
                Integer id = (Integer) dept.get("id");
                String name = (String) dept.get("name");
                if (id != null && departmentId.equals(id.toString())) {
                    return name != null ? name : "部门" + departmentId;
                }
            }
        } catch (Exception e) {
            log.error("获取部门名称失败", e);
        }

        // 如果找不到，根据已知部门列表返回
        return getDepartmentNameFromStaticList(departmentId);
    }

    /**
     * 从静态部门列表获取部门名称（备用方法）
     */
    private String getDepartmentNameFromStaticList(String departmentId) {
        switch (departmentId) {
            case "1": return "小白跃升坊";
            case "2": return "其他（待设置部门）";
            case "3": return "华东区域";
            case "4": return "华南区域";
            case "5": return "华北区域";
            case "6": return "华中区域";
            case "7": return "智能体开发";
            case "8": return "小程序开发";
            default: return "部门" + departmentId;
        }
    }

    /**
     * 根据区域名称找到对应的区域部门ID
     */
    private String findRegionDepartmentId(String regionName) {
        if (regionName == null || regionName.isEmpty()) {
            return null;
        }

        // 根据区域名称映射到部门ID
        switch (regionName) {
            case "华东区域": return "3";
            case "华南区域": return "4";
            case "华北区域": return "5";
            case "华中区域": return "6";
            case "智能体开发": return "7";
            case "小程序开发": return "8";
            default:
                // 尝试从部门列表中查找
                return findDepartmentIdByName(regionName);
        }
    }

    /**
     * 从部门列表中根据名称查找部门ID
     */
    private String findDepartmentIdByName(String departmentName) {
        try {
            List<Map<String, Object>> departments = departmentService.getDepartmentList();
            for (Map<String, Object> dept : departments) {
                String name = (String) dept.get("name");
                Integer id = (Integer) dept.get("id");
                if (departmentName.equals(name)) {
                    return id.toString();
                }
            }
        } catch (Exception e) {
            log.error("查找部门ID失败", e);
        }
        return null;
    }

    /**
     * 验证部门ID格式
     */
    private boolean isValidDepartmentId(String departmentId) {
        if (departmentId == null || departmentId.trim().isEmpty()) {
            return false;
        }

        try {
            // 部门ID应该是数字
            Long.parseLong(departmentId.trim());
            return true;
        } catch (NumberFormatException e) {
            log.warn("部门ID不是有效数字: {}", departmentId);
            return false;
        }
    }

    /**
     * 构造发票表格数据
     */
    private List<WeComApprovalRequest.Child> buildInvoiceTableData(List<InvoiceInfo> invoices, String mediaIds) {
        List<WeComApprovalRequest.Child> children = new ArrayList<>();

        // 解析mediaIds，确保顺序与invoices一致
        String[] fileIdArray = parseMediaIds(mediaIds, invoices.size());

        log.info("解析mediaIds结果: {}, 发票数量: {}", Arrays.toString(fileIdArray), invoices.size());

        for (int i = 0; i < invoices.size(); i++) {
            InvoiceInfo invoice = invoices.get(i);
            String fileId = i < fileIdArray.length ? fileIdArray[i] : null;

            log.info("处理第 {} 张发票, fileId: {}, 发票项目: {}", i + 1, fileId, invoice.getInvoiceItemName());

            WeComApprovalRequest.Child child = new WeComApprovalRequest.Child();
            List<WeComApprovalRequest.ListItem> listItems = new ArrayList<>();

            // 1. 费用类型选择器 - item-1503317870534
            WeComApprovalRequest.ListItem typeItem = new WeComApprovalRequest.ListItem();
            typeItem.setControl("Selector");
            typeItem.setId("item-1503317870534");
            WeComApprovalRequest.SelectorValue typeValue = new WeComApprovalRequest.SelectorValue();
            WeComApprovalRequest.Selector selector = new WeComApprovalRequest.Selector();
            selector.setType("single");
            WeComApprovalRequest.Option option = new WeComApprovalRequest.Option();

            // 根据费用类型映射到模板中的选项key
            String expenseTypeKey = mapExpenseTypeToKey(invoice.getReimbursementType(), invoice.getSubReimbursementType());
            option.setKey(expenseTypeKey);

            WeComApprovalRequest.TextValue textValue = new WeComApprovalRequest.TextValue();
            String displayText = buildExpenseTypeDisplayText(invoice.getReimbursementType(), invoice.getSubReimbursementType());
            textValue.setText(displayText);
            textValue.setLang("zh_CN");
            option.setValue(Arrays.asList(textValue));
            selector.setOptions(Arrays.asList(option));
            typeValue.setSelector(selector);
            typeItem.setValue(typeValue);
            listItems.add(typeItem);

            // 2. 金额控件 - item-1503317989302
            WeComApprovalRequest.ListItem moneyItem = new WeComApprovalRequest.ListItem();
            moneyItem.setControl("Money");
            moneyItem.setId("item-1503317989302");
            WeComApprovalRequest.MoneyValue moneyValue = new WeComApprovalRequest.MoneyValue();
            // 确保金额格式正确，移除可能的"元"字符
            String amountStr = invoice.getTotalAmount();
            if (amountStr != null) {
                amountStr = amountStr.replace("元", "").replace(" ", "").trim();
            }
            moneyValue.setNew_money(amountStr);
            moneyItem.setValue(moneyValue);
            listItems.add(moneyItem);

            // 3. 日期控件 - item-1503317973968
            WeComApprovalRequest.ListItem dateItem = new WeComApprovalRequest.ListItem();
            dateItem.setControl("Date");
            dateItem.setId("item-1503317973968");
            WeComApprovalRequest.DateValue dateValue = new WeComApprovalRequest.DateValue();
            WeComApprovalRequest.Date date = new WeComApprovalRequest.Date();
            date.setType("day");
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            if (invoice.getInvoiceDate() != null && !invoice.getInvoiceDate().isEmpty()) {
                try {
                    LocalDate invoiceDate = LocalDate.parse(invoice.getInvoiceDate());
                    timestamp = String.valueOf(invoiceDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond());
                } catch (Exception e) {
                    log.warn("解析发票日期失败，使用当前时间: {}", invoice.getInvoiceDate());
                }
            }
            date.setS_timestamp(timestamp);
            dateValue.setDate(date);
            dateItem.setValue(dateValue);
            listItems.add(dateItem);

            // 4. 文件控件（发票）- File-1761553598530
            WeComApprovalRequest.ListItem fileItem = new WeComApprovalRequest.ListItem();
            fileItem.setControl("File");
            fileItem.setId("File-1761553598530");
            WeComApprovalRequest.FileValue fileValue = new WeComApprovalRequest.FileValue();

            List<WeComApprovalRequest.File> files = new ArrayList<>();
            if (fileId != null && !fileId.trim().isEmpty()) {
                WeComApprovalRequest.File file = new WeComApprovalRequest.File();
                file.setFile_id(fileId.trim());
                files.add(file);
                log.info("为发票 {} 设置文件ID: {}", i + 1, fileId.trim());
            } else {
                log.warn("发票 {} 没有对应的文件ID", i + 1);
            }
            fileValue.setFiles(files);
            fileItem.setValue(fileValue);
            listItems.add(fileItem);

            // 5. 其他说明文本框 - item-1503318001306
            WeComApprovalRequest.ListItem descItem = new WeComApprovalRequest.ListItem();
            descItem.setControl("Textarea");
            descItem.setId("item-1503318001306");
            WeComApprovalRequest.TextareaValue descValue = new WeComApprovalRequest.TextareaValue();
            String descText = "发票号码：" + (invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "") +
                    "，销售方：" + (invoice.getSellerName() != null ? invoice.getSellerName() : "") +
                    (invoice.getRemark() != null && !invoice.getRemark().isEmpty() ? "，备注：" + invoice.getRemark() : "");
            descValue.setText(descText);
            descItem.setValue(descValue);
            listItems.add(descItem);

            // 6. 消费事由文本框 - Textarea-1761553740099
            WeComApprovalRequest.ListItem expenseDescItem = new WeComApprovalRequest.ListItem();
            expenseDescItem.setControl("Textarea");
            expenseDescItem.setId("Textarea-1761553740099");
            WeComApprovalRequest.TextareaValue expenseDescValue = new WeComApprovalRequest.TextareaValue();
            String expenseDesc = invoice.getConsumptionReason() != null ? invoice.getConsumptionReason() :
                    (invoice.getReimbursementReason() != null ? invoice.getReimbursementReason() :
                            (invoice.getReimbursementType() != null ? invoice.getReimbursementType() : "日常报销"));
            expenseDescValue.setText(expenseDesc);
            expenseDescItem.setValue(expenseDescValue);
            listItems.add(expenseDescItem);

            // 7. 附件文件控件 - File-1761553796412
            WeComApprovalRequest.ListItem attachmentFileItem = new WeComApprovalRequest.ListItem();
            attachmentFileItem.setControl("File");
            attachmentFileItem.setId("File-1761553796412");
            WeComApprovalRequest.FileValue attachmentFileValue = new WeComApprovalRequest.FileValue();
            attachmentFileValue.setFiles(new ArrayList<>()); // 空文件列表
            attachmentFileItem.setValue(attachmentFileValue);
            listItems.add(attachmentFileItem);

            child.setList(listItems);
            children.add(child);
        }

        return children;
    }

    /**
     * 解析mediaIds字符串，确保与发票数量匹配
     */
    private String[] parseMediaIds(String mediaIds, int invoiceCount) {
        if (mediaIds == null || mediaIds.trim().isEmpty()) {
            log.warn("mediaIds为空，返回空数组");
            return new String[0];
        }

        String[] fileIdArray = mediaIds.split(",");
        log.info("解析mediaIds: {}, 分割后数量: {}, 发票数量: {}", mediaIds, fileIdArray.length, invoiceCount);

        // 如果文件ID数量与发票数量不匹配，记录警告
        if (fileIdArray.length != invoiceCount) {
            log.warn("文件ID数量({})与发票数量({})不匹配", fileIdArray.length, invoiceCount);
        }

        // 清理文件ID，移除前后空格
        for (int i = 0; i < fileIdArray.length; i++) {
            fileIdArray[i] = fileIdArray[i].trim();
        }

        return fileIdArray;
    }

    /**
     * 将费用类型映射到模板中的选项key
     */
    private String mapExpenseTypeToKey(String reimbursementType, String subReimbursementType) {
        if (reimbursementType == null) {
            return "option-3085548583"; // 默认交通费
        }

        // 根据模板中的选项进行精确映射
        switch (reimbursementType) {
            case "交通费":
                return "option-3085548583";
            case "福利费":
                if ("团建".equals(subReimbursementType)) return "option-3085548584";
                if ("旅游费".equals(subReimbursementType)) return "option-1761552794141";
                if ("周年庆".equals(subReimbursementType)) return "option-1761552809300";
                if ("其他福利费".equals(subReimbursementType)) return "option-1761552816412";
                return "option-3085548584"; // 默认团建
            case "办公费":
                if ("饮用水费".equals(subReimbursementType)) return "option-3085548585";
                if ("日常办公用品".equals(subReimbursementType)) return "option-1761552836701";
                if ("报刊杂志".equals(subReimbursementType)) return "option-1761552847789";
                if ("耗材费".equals(subReimbursementType)) return "option-1761552854309";
                if ("打印".equals(subReimbursementType)) return "option-1761552860349";
                if ("其他办公费".equals(subReimbursementType)) return "option-1761552865525";
                return "option-3085548585"; // 默认饮用水费
            case "快递费":
                return "option-3085548586";
            case "业务招待费":
                return "option-3085548587";
            case "礼品费":
                return "option-3085548588";
            case "技术服务费":
                return "option-3085548589";
            case "会议费":
                return "option-3085548590";
            case "培训费":
                return "option-3085548591";
            case "中介服务费":
                return "option-3085548592";
            case "推广费":
                return "option-1761552725411";
            case "POC费用":
                return "option-1761552731179";
            case "咨询费":
                return "option-1761552737355";
            case "中介机构费":
                return "option-1761552743667";
            default:
                return "option-3085548583"; // 默认交通费
        }
    }

    /**
     * 构建费用类型显示文本
     */
    private String buildExpenseTypeDisplayText(String reimbursementType, String subReimbursementType) {
        if (reimbursementType == null) {
            return "交通费";
        }

        // 对于福利费和办公费，需要显示子类型
        if ("福利费".equals(reimbursementType) || "办公费".equals(reimbursementType)) {
            if (subReimbursementType != null && !subReimbursementType.isEmpty()) {
                return reimbursementType + "/" + subReimbursementType;
            } else {
                // 如果没有子类型，使用默认值
                if ("福利费".equals(reimbursementType)) return "福利费/团建";
                if ("办公费".equals(reimbursementType)) return "办公费/饮用水费";
            }
        }

        return reimbursementType;
    }

    /**
     * 将费用类型映射到客成差旅模板中的选项key
     */
    private String mapTravelExpenseTypeToKey(String reimbursementType, String subReimbursementType) {
        if (reimbursementType == null) {
            return "option-1761703042707"; // 默认交通费
        }

        // 根据新的客成差旅模板选项进行映射
        switch (reimbursementType) {
            case "交通费":
                return "option-1761703042707";
            case "快递费":
                return "option-1763606878156";
            case "业务招待费":
                return "option-1763606878020";
            case "礼品费":
                return "option-1763606877884";
            case "技术服务费":
                return "option-1763606877748";
            case "会议费":
                return "option-1763606877604";
            case "培训费":
                return "option-1763606877364";
            case "中介服务费":
                return "option-1763606877228";
            case "推广费":
                return "option-1763606877076";
            case "POC费用":
                return "option-1763606876916";
            case "咨询费":
                return "option-1763606934101";
            case "中介机构费":
                return "option-1763606933829";
            case "差旅成本":
                return "option-1764553689630";
            // 福利费和办公费需要特殊处理，因为新模板中没有子类型选项
            case "福利费":
                // 福利费在新模板中没有对应选项，使用默认值
                return "option-1761703042707"; // 默认交通费
            case "办公费":
                // 办公费在新模板中没有对应选项，使用默认值
                return "option-1761703042707"; // 默认交通费
            default:
                return "option-1761703042707"; // 默认交通费
        }
    }

    /**
     * 构建客成差旅费用类型显示文本
     */
    private String buildTravelExpenseTypeDisplayText(String reimbursementType, String subReimbursementType) {
        if (reimbursementType == null) {
            return "交通费"; // 客成差旅默认显示文本
        }

        // 客成差旅模板不支持福利费和办公费的子类型，直接返回主类型
        // 如果用户选择了福利费或办公费，需要提示用户选择其他类型
        if ("福利费".equals(reimbursementType) || "办公费".equals(reimbursementType)) {
            log.warn("客成差旅报销不支持 {} 类型，建议用户选择其他费用类型", reimbursementType);
            // 返回主类型，但实际提交时会映射到交通费
            return reimbursementType;
        }

        return reimbursementType;
    }

    /**
     * 构造摘要信息
     */
    private List<WeComApprovalRequest.Summary> buildSummaryList(String userId, List<InvoiceInfo> invoices, String totalAmount, String legalEntity, String region, String costDepartment) {
        List<WeComApprovalRequest.Summary> summaries = new ArrayList<>();

        // 第一行摘要：报销类型和申请人
        WeComApprovalRequest.Summary summary1 = new WeComApprovalRequest.Summary();
        List<WeComApprovalRequest.SummaryInfo> summaryInfo1 = new ArrayList<>();
        WeComApprovalRequest.SummaryInfo info1 = new WeComApprovalRequest.SummaryInfo();
        // 使用实际用户姓名
        String actualUserName = getActualUserName(userId); // 获取实际用户姓名
        info1.setText("日常报销申请 - " + actualUserName);
        info1.setLang("zh_CN");
        summaryInfo1.add(info1);
        summary1.setSummary_info(summaryInfo1);
        summaries.add(summary1);

        // 第二行摘要：法人实体和区域
        WeComApprovalRequest.Summary summary2 = new WeComApprovalRequest.Summary();
        List<WeComApprovalRequest.SummaryInfo> summaryInfo2 = new ArrayList<>();
        WeComApprovalRequest.SummaryInfo info2 = new WeComApprovalRequest.SummaryInfo();

        String costDeptName = getDepartmentNameById(costDepartment);
        String regionDeptName = getDepartmentNameById(findRegionDepartmentId(region));

        String entityInfo = "法人实体：" + (legalEntity != null ? legalEntity : "杭州飞致云信息科技有限公司（CODE1）") +
                "，区域：" + (regionDeptName != null ? regionDeptName : "华南区域") +
                "，成本部门：" + (costDeptName != null ? costDeptName : "智能体开发");

        info2.setText(entityInfo);
        info2.setLang("zh_CN");
        summaryInfo2.add(info2);
        summary2.setSummary_info(summaryInfo2);
        summaries.add(summary2);

        // 第三行摘要：报销日期范围
        WeComApprovalRequest.Summary summary3 = new WeComApprovalRequest.Summary();
        List<WeComApprovalRequest.SummaryInfo> summaryInfo3 = new ArrayList<>();
        WeComApprovalRequest.SummaryInfo info3 = new WeComApprovalRequest.SummaryInfo();
        info3.setText("报销日期：" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) +
                "（含" + LocalDate.now().minusMonths(3).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) +
                "至" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "期间费用）");
        info3.setLang("zh_CN");
        summaryInfo3.add(info3);
        summary3.setSummary_info(summaryInfo3);
        summaries.add(summary3);

        // 第四行摘要：总金额明细
        WeComApprovalRequest.Summary summary4 = new WeComApprovalRequest.Summary();
        List<WeComApprovalRequest.SummaryInfo> summaryInfo4 = new ArrayList<>();
        WeComApprovalRequest.SummaryInfo info4 = new WeComApprovalRequest.SummaryInfo();

        // 处理总金额显示，确保格式正确
        String displayTotalAmount = totalAmount;
        if (displayTotalAmount != null && !displayTotalAmount.isEmpty()) {
            // 移除可能的"元"字符
            displayTotalAmount = displayTotalAmount.replace("元", "").trim();
            if (!displayTotalAmount.isEmpty()) {
                try {
                    // 确保是有效数字格式
                    new BigDecimal(displayTotalAmount);
                    displayTotalAmount += "元";
                } catch (NumberFormatException e) {
                    // 如果不是有效数字，使用默认值
                    displayTotalAmount = totalAmount; // 保持原始值
                }
            }
        }

        info4.setText("总金额：" + displayTotalAmount + calculateExpenseDetails(invoices));
        info4.setLang("zh_CN");
        summaryInfo4.add(info4);
        summary4.setSummary_info(summaryInfo4);
        summaries.add(summary4);

        return summaries;
    }

    /**
     * 确定报销类型描述
     */
    private String determineExpenseType(List<InvoiceInfo> invoices) {
        Set<String> types = invoices.stream()
                .map(InvoiceInfo::getReimbursementType)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (types.size() == 1) {
            return types.iterator().next();
        } else if (types.size() > 1) {
            return String.join("+", types);
        } else {
            return "通用费用";
        }
    }

    /**
     * 计算各类费用明细
     */
    private String calculateExpenseDetails(List<InvoiceInfo> invoices) {
        Map<String, BigDecimal> typeTotals = new HashMap<>();

        for (InvoiceInfo invoice : invoices) {
            String type = invoice.getReimbursementType() != null ? invoice.getReimbursementType() : "其他";
            // 修复金额解析逻辑，去除可能的"元"等单位字符
            String amountStr = invoice.getTotalAmount();
            if (amountStr != null) {
                // 移除"元"、空格等非数字字符，只保留数字、小数点和负号
                amountStr = amountStr.replaceAll("[^0-9.-]", "");
                if (!amountStr.isEmpty()) {
                    try {
                        BigDecimal amount = new BigDecimal(amountStr);
                        typeTotals.merge(type, amount, BigDecimal::add);
                    } catch (NumberFormatException e) {
                        log.warn("解析发票金额失败，发票类型: {}, 金额字符串: {}", type, invoice.getTotalAmount());
                        // 如果解析失败，跳过该发票
                    }
                }
            }
        }

        if (typeTotals.isEmpty()) {
            return "";
        }

        StringBuilder details = new StringBuilder("（");
        boolean first = true;
        for (Map.Entry<String, BigDecimal> entry : typeTotals.entrySet()) {
            if (!first) {
                details.append(" + ");
            }
            details.append(entry.getKey()).append(entry.getValue().toString()).append("元");
            first = false;
        }
        details.append("）");

        return details.toString();
    }

    /**
     * 获取实际提交人姓名
     */
    private String getActualUserName(String userId) {
        try {
            // 调用企业微信API获取用户详情
            String accessToken = qyWechatService.getAccessTokenForApproval();
            String url = "https://qyapi.weixin.qq.com/cgi-bin/user/get?access_token=" + accessToken + "&userid=" + userId;

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer errcode = (Integer) responseBody.get("errcode");

                if (errcode != null && errcode == 0) {
                    String name = (String) responseBody.get("name");
                    if (name != null && !name.trim().isEmpty()) {
                        log.info("获取到用户 {} 的真实姓名: {}", userId, name);
                        return name;
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取用户信息失败，使用默认名称", e);
        }

        // 如果获取失败，使用userId作为备选
        log.warn("无法获取用户 {} 的真实姓名，使用userId作为显示名称", userId);
        return userId;
    }
}