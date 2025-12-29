package com.fit2cloud.fapiao.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fit2cloud.fapiao.dto.request.WeComApprovalRequest;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class BusinessTripService {

    @Value("${qywechat.approval.template-id.business-trip:}")
    private String businessTripTemplateId;

    @Autowired
    private QyWechatService qyWechatService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DepartmentService departmentService;

    /**
     * 提交出差申请单
     */
    public String submitBusinessTripApproval(String userId, String reason, String customerName,
                                             String applyDate, String departmentId,
                                             String travelStartDate, String travelEndDate,
                                             String travelStartPeriod, String travelEndPeriod,
                                             String travelDays) {
        try {
            log.info("开始提交出差申请单, 申请人: {}, 出差事由: {}, 客户名称: {}, 申请部门: {}",
                    userId, reason, customerName, departmentId);

            if (businessTripTemplateId == null || businessTripTemplateId.isEmpty()) {
                throw new BusinessException("未配置出差申请单模板ID");
            }

            // 获取access token
            String accessToken = qyWechatService.getAccessTokenForApproval();

            // 构造出差申请请求
            WeComApprovalRequest approvalRequest = buildBusinessTripApprovalRequest(
                    userId, reason, customerName, applyDate, departmentId,
                    travelStartDate, travelEndDate, travelStartPeriod, travelEndPeriod, travelDays);

            // 调试：打印请求数据
            ObjectMapper mapper = new ObjectMapper();
            String requestJson = mapper.writeValueAsString(approvalRequest);
            log.info("出差申请单请求数据: {}", requestJson);

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
                    log.info("出差申请单提交成功, 审批编号: {}", spNo);
                    return spNo;
                } else {
                    String errmsg = (String) responseBody.get("errmsg");
                    log.error("出差申请单提交失败, 错误码: {}, 错误信息: {}", errcode, errmsg);
                    throw new BusinessException("提交出差申请失败: " + errmsg);
                }
            } else {
                throw new BusinessException("提交出差申请请求失败: " + response.getStatusCode());
            }
        } catch (BusinessException e) {
            log.error("提交出差申请单业务异常", e);
            throw e;
        } catch (Exception e) {
            log.error("提交出差申请单异常", e);
            throw new BusinessException("提交出差申请异常: " + e.getMessage());
        }
    }

    /**
     * 构造出差申请单请求对象
     */
    private WeComApprovalRequest buildBusinessTripApprovalRequest(String userId, String reason, String customerName,
                                                                  String applyDate, String departmentId,
                                                                  String travelStartDate, String travelEndDate,
                                                                  String travelStartPeriod, String travelEndPeriod,
                                                                  String travelDays) {
        WeComApprovalRequest request = new WeComApprovalRequest();

        // 基本信息
        request.setCreator_userid(userId);
        request.setTemplate_id(businessTripTemplateId);
        request.setUse_template_approver(0); // 不使用模板审批人

        // 审批人设置（可以根据需要调整）
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

        // 构造申请数据
        request.setApply_data(buildBusinessTripApplyData(userId, reason, customerName, applyDate, departmentId,
                travelStartDate, travelEndDate, travelStartPeriod, travelEndPeriod, travelDays));

        // 摘要信息
        request.setSummary_list(buildBusinessTripSummaryList(userId, reason, customerName, departmentId, travelDays));

        return request;
    }

    /**
     * 构造出差申请单申请数据
     */
    private WeComApprovalRequest.ApplyData buildBusinessTripApplyData(String userId, String reason, String customerName,
                                                                      String applyDate, String departmentId,
                                                                      String travelStartDate, String travelEndDate,
                                                                      String travelStartPeriod, String travelEndPeriod,
                                                                      String travelDays) {
        WeComApprovalRequest.ApplyData applyData = new WeComApprovalRequest.ApplyData();
        List<WeComApprovalRequest.Content> contents = new ArrayList<>();

        // 1. 出差事由
        WeComApprovalRequest.Content reasonInput = new WeComApprovalRequest.Content();
        reasonInput.setControl("Textarea");
        reasonInput.setId("item-1497581558567");
        WeComApprovalRequest.TextareaValue reasonValue = new WeComApprovalRequest.TextareaValue();
        reasonValue.setText(reason != null ? reason : "出差申请");
        reasonInput.setValue(reasonValue);
        contents.add(reasonInput);

        // 2. 客户名称
        if (customerName != null && !customerName.trim().isEmpty()) {
            WeComApprovalRequest.Content customerNameInput = new WeComApprovalRequest.Content();
            customerNameInput.setControl("Text");
            customerNameInput.setId("Text-1763707371367");
            WeComApprovalRequest.TextareaValue customerValue = new WeComApprovalRequest.TextareaValue();
            customerValue.setText(customerName.trim());
            customerNameInput.setValue(customerValue);
            contents.add(customerNameInput);
        }

        // 3. 提交人员
        WeComApprovalRequest.Content contact = new WeComApprovalRequest.Content();
        contact.setControl("Contact");
        contact.setId("Contact-1763707352207");
        WeComApprovalRequest.ContactValue contactValue = new WeComApprovalRequest.ContactValue();
        WeComApprovalRequest.Member member = new WeComApprovalRequest.Member();
        member.setUserid(userId);
        // 需要获取实际提交人的姓名，这里需要调用企业微信API获取用户信息
        String actualUserName = getActualUserName(userId);
        member.setName(actualUserName);
        contactValue.setMembers(Arrays.asList(member));
        contact.setValue(contactValue);
        contents.add(contact);

        // 4. 申请日期
        WeComApprovalRequest.Content date = new WeComApprovalRequest.Content();
        date.setControl("Date");
        date.setId("Date-1763707513994");
        WeComApprovalRequest.DateValue dateValue = new WeComApprovalRequest.DateValue();
        WeComApprovalRequest.Date dateObj = new WeComApprovalRequest.Date();
        dateObj.setType("day");

        // 转换申请日期为时间戳
        String timestamp;
        if (applyDate != null && !applyDate.trim().isEmpty()) {
            try {
                // 修复：使用明确的日期格式解析
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

                // 处理可能的ISO 8601格式（如：2025-11-03T16:00:00.000Z）
                String cleanDate = applyDate;
                if (applyDate.contains("T")) {
                    cleanDate = applyDate.substring(0, 10); // 提取yyyy-MM-dd部分
                    log.info("检测到ISO 8601格式日期，提取前10位: {} -> {}", applyDate, cleanDate);
                }

                // 解析报销日期格式 "yyyy-MM-dd"
                LocalDate applyLocalDate = LocalDate.parse(cleanDate, formatter);
                timestamp = String.valueOf(applyLocalDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond());
                log.info("使用报销日期作为申请日期: {}, 时间戳: {}", cleanDate, timestamp);
            } catch (Exception e) {
                log.warn("解析报销日期失败: {}, 使用当前时间", applyDate, e);
                timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            }
        } else {
            // 如果报销日期为空，使用当前时间
            timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            log.warn("报销日期为空，使用当前时间作为申请日期");
        }

        dateObj.setS_timestamp(timestamp);
        dateValue.setDate(dateObj);
        date.setValue(dateValue);
        contents.add(date);

        // 5. 申请部门
        if (departmentId != null && !departmentId.trim().isEmpty() && isValidDepartmentId(departmentId)) {
            WeComApprovalRequest.Content deptContact = new WeComApprovalRequest.Content();
            deptContact.setControl("Contact");
            deptContact.setId("Contact-1763707536538");
            WeComApprovalRequest.ContactValue deptValue = new WeComApprovalRequest.ContactValue();

            WeComApprovalRequest.Member deptMember = new WeComApprovalRequest.Member();
            deptMember.setPartyid(Arrays.asList(departmentId.trim()));
            String deptName = getDepartmentNameById(departmentId);
            deptMember.setName(deptName != null ? deptName : "部门" + departmentId);
            deptValue.setMembers(Arrays.asList(deptMember));

            deptContact.setValue(deptValue);
            contents.add(deptContact);
        }

        // 6. 时长（日期范围）
        if (travelStartDate != null && !travelStartDate.trim().isEmpty() &&
                travelEndDate != null && !travelEndDate.trim().isEmpty()) {

            WeComApprovalRequest.Content travelDaysRange = new WeComApprovalRequest.Content();
            travelDaysRange.setControl("DateRange");
            travelDaysRange.setId("DateRange-1763709480497");

            WeComApprovalRequest.DateRangeValue dateRangeValue = new WeComApprovalRequest.DateRangeValue();
            WeComApprovalRequest.DateRange dateRange = new WeComApprovalRequest.DateRange();
            dateRange.setType("halfday");

            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                ZoneId zoneId = ZoneId.systemDefault();

                LocalDate startDate = LocalDate.parse(travelStartDate, formatter);
                LocalDate endDate = LocalDate.parse(travelEndDate, formatter);

                long startTimestamp, endTimestamp;

                // 计算时间戳
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

                long duration = endTimestamp - startTimestamp;

                dateRange.setNew_begin(String.valueOf(startTimestamp));
                dateRange.setNew_end(String.valueOf(endTimestamp));
                dateRange.setNew_duration(duration);

                log.info("设置出差时长: {} {} 至 {} {}, new_begin: {}, new_end: {}, new_duration: {}秒",
                        travelStartDate, travelStartPeriod, travelEndDate, travelEndPeriod,
                        startTimestamp, endTimestamp, duration);

            } catch (Exception e) {
                log.warn("日期转换失败，使用当前时间作为默认值", e);
                long currentTime = System.currentTimeMillis() / 1000;
                dateRange.setNew_begin(String.valueOf(currentTime - 43200));
                dateRange.setNew_end(String.valueOf(currentTime));
                dateRange.setNew_duration(43200L);
            }

            dateRangeValue.setDate_range(dateRange);
            travelDaysRange.setValue(dateRangeValue);
            contents.add(travelDaysRange);
        }

        applyData.setContents(contents);
        return applyData;
    }

    /**
     * 构造出差申请单摘要信息
     */
    private List<WeComApprovalRequest.Summary> buildBusinessTripSummaryList(String userId, String reason,
                                                                            String customerName, String departmentId,
                                                                            String travelDays) {
        List<WeComApprovalRequest.Summary> summaries = new ArrayList<>();

        // 第一行摘要：出差申请基本信息
        WeComApprovalRequest.Summary summary1 = new WeComApprovalRequest.Summary();
        List<WeComApprovalRequest.SummaryInfo> summaryInfo1 = new ArrayList<>();
        WeComApprovalRequest.SummaryInfo info1 = new WeComApprovalRequest.SummaryInfo();

        String customerInfo = customerName != null && !customerName.trim().isEmpty() ?
                "客户：" + customerName : "客户：未填写";

        String daysInfo = travelDays != null && !travelDays.isEmpty() ?
                "，出差天数：" + travelDays + "天" : "";

        // 使用实际用户姓名
        String actualUserName = getActualUserName(userId); // 获取实际用户姓名
        info1.setText("出差申请 - " + actualUserName + "，" + customerInfo + daysInfo);
        info1.setLang("zh_CN");
        summaryInfo1.add(info1);
        summary1.setSummary_info(summaryInfo1);
        summaries.add(summary1);

        // 第二行摘要：申请部门和事由
        WeComApprovalRequest.Summary summary2 = new WeComApprovalRequest.Summary();
        List<WeComApprovalRequest.SummaryInfo> summaryInfo2 = new ArrayList<>();
        WeComApprovalRequest.SummaryInfo info2 = new WeComApprovalRequest.SummaryInfo();

        String deptName = getDepartmentNameById(departmentId);
        String reasonSummary = reason != null && reason.length() > 20 ?
                reason.substring(0, 20) + "..." : reason;

        info2.setText("申请部门：" + (deptName != null ? deptName : "未选择") +
                "，事由：" + (reasonSummary != null ? reasonSummary : "出差"));
        info2.setLang("zh_CN");
        summaryInfo2.add(info2);
        summary2.setSummary_info(summaryInfo2);
        summaries.add(summary2);

        return summaries;
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