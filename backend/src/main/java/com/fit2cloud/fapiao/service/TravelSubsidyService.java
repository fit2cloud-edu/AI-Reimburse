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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TravelSubsidyService {

    @Value("${qywechat.approval.template-id.travel-subsidy:}")
    private String travelSubsidyTemplateId;

    @Autowired
    private QyWechatService qyWechatService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DepartmentService departmentService;

    /**
     * 提交出差补贴申请单
     */
    public String submitTravelSubsidyApproval(String userId, String userName, String reason,
                                              String applyDate, String departmentId,
                                              String travelStartDate, String travelEndDate,
                                              String travelStartPeriod, String travelEndPeriod,
                                              String travelDays, String relatedApprovalNo) {
        try {
            log.info("开始提交出差补贴申请单, 申请人: {} ({}), 出差天数: {}, 关联出差申请: {}",
                    userName, userId, travelDays, relatedApprovalNo);

            if (travelSubsidyTemplateId == null || travelSubsidyTemplateId.isEmpty()) {
                throw new BusinessException("未配置出差补贴申请单模板ID");
            }

            // 计算补贴金额
            BigDecimal subsidyAmount = calculateSubsidyAmount(travelDays);
            log.info("出差补贴金额计算: {} 天 -> {} 元", travelDays, subsidyAmount);

            // 获取access token
            String accessToken = qyWechatService.getAccessTokenForApproval();

            // 构造出差补贴申请请求
            WeComApprovalRequest approvalRequest = buildTravelSubsidyApprovalRequest(
                    userId, userName, reason, applyDate, departmentId,
                    travelStartDate, travelEndDate, travelStartPeriod, travelEndPeriod,
                    travelDays, subsidyAmount, relatedApprovalNo);

            // 调试：打印请求数据
            ObjectMapper mapper = new ObjectMapper();
            String requestJson = mapper.writeValueAsString(approvalRequest);
            log.info("出差补贴申请单请求数据: {}", requestJson);

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
                    log.info("出差补贴申请单提交成功, 审批编号: {}", spNo);
                    return spNo;
                } else {
                    String errmsg = (String) responseBody.get("errmsg");
                    log.error("出差补贴申请单提交失败, 错误码: {}, 错误信息: {}", errcode, errmsg);
                    throw new BusinessException("提交出差补贴申请失败: " + errmsg);
                }
            } else {
                throw new BusinessException("提交出差补贴申请请求失败: " + response.getStatusCode());
            }
        } catch (BusinessException e) {
            log.error("提交出差补贴申请单业务异常", e);
            throw e;
        } catch (Exception e) {
            log.error("提交出差补贴申请单异常", e);
            throw new BusinessException("提交出差补贴申请异常: " + e.getMessage());
        }
    }

    /**
     * 计算补贴金额
     */
    private BigDecimal calculateSubsidyAmount(String travelDays) {
        if (travelDays == null || travelDays.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }

        try {
            double days = Double.parseDouble(travelDays);
            // 0.5天 = 50元, 1天 = 100元
            BigDecimal amount = BigDecimal.valueOf(days * 100);
            return amount.setScale(2, BigDecimal.ROUND_HALF_UP);
        } catch (NumberFormatException e) {
            log.warn("解析出差天数失败: {}, 使用0元补贴", travelDays);
            return BigDecimal.ZERO;
        }
    }

    /**
     * 构造出差补贴申请单请求对象
     */
    private WeComApprovalRequest buildTravelSubsidyApprovalRequest(String userId, String userName, String reason,
                                                                   String applyDate, String departmentId,
                                                                   String travelStartDate, String travelEndDate,
                                                                   String travelStartPeriod, String travelEndPeriod,
                                                                   String travelDays, BigDecimal subsidyAmount,
                                                                   String relatedApprovalNo) {
        WeComApprovalRequest request = new WeComApprovalRequest();

        // 基本信息
        request.setCreator_userid(userId);
        request.setTemplate_id(travelSubsidyTemplateId);
        request.setUse_template_approver(0);

        // 审批人设置
        List<WeComApprovalRequest.Approver> approvers = new ArrayList<>();

        WeComApprovalRequest.Approver approver1 = new WeComApprovalRequest.Approver();
        approver1.setAttr(2); // 直接上级
        approver1.setUserid(Arrays.asList(userId));
        approvers.add(approver1);

        WeComApprovalRequest.Approver approver2 = new WeComApprovalRequest.Approver();
        approver2.setAttr(1); // 固定审批人
        approver2.setUserid(Arrays.asList("XueQi"));
        approvers.add(approver2);

        request.setApprover(approvers);
        request.setNotifyer(Arrays.asList(userId));
        request.setNotify_type(1);

        // 构造申请数据
        request.setApply_data(buildTravelSubsidyApplyData(userId, userName, reason, applyDate, departmentId,
                travelStartDate, travelEndDate, travelStartPeriod, travelEndPeriod,
                travelDays, subsidyAmount, relatedApprovalNo));

        // 摘要信息
        request.setSummary_list(buildTravelSubsidySummaryList(userId, userName, travelDays, subsidyAmount, relatedApprovalNo));

        return request;
    }

    /**
     * 构造出差补贴申请单申请数据
     */
    private WeComApprovalRequest.ApplyData buildTravelSubsidyApplyData(String userId, String userName, String reason,
                                                                       String applyDate, String departmentId,
                                                                       String travelStartDate, String travelEndDate,
                                                                       String travelStartPeriod, String travelEndPeriod,
                                                                       String travelDays, BigDecimal subsidyAmount,
                                                                       String relatedApprovalNo) {
        WeComApprovalRequest.ApplyData applyData = new WeComApprovalRequest.ApplyData();
        List<WeComApprovalRequest.Content> contents = new ArrayList<>();

        // 1. 报销类型选择器 - 固定为出差补贴申请
        WeComApprovalRequest.Content typeSelector = new WeComApprovalRequest.Content();
        typeSelector.setControl("Selector");
        typeSelector.setId("item-1503317593875");
        WeComApprovalRequest.SelectorValue typeValue = new WeComApprovalRequest.SelectorValue();
        WeComApprovalRequest.Selector typeSelectorObj = new WeComApprovalRequest.Selector();
        typeSelectorObj.setType("single");
        WeComApprovalRequest.Option typeOption = new WeComApprovalRequest.Option();
        typeOption.setKey("option-127158625");
        WeComApprovalRequest.TextValue typeTextValue = new WeComApprovalRequest.TextValue();
        typeTextValue.setText("出差补贴申请");
        typeTextValue.setLang("zh_CN");
        typeOption.setValue(Arrays.asList(typeTextValue));
        typeSelectorObj.setOptions(Arrays.asList(typeOption));
        typeValue.setSelector(typeSelectorObj);
        typeSelector.setValue(typeValue);
        contents.add(typeSelector);

        // 2. 报销事由
        WeComApprovalRequest.Content reasonInput = new WeComApprovalRequest.Content();
        reasonInput.setControl("Textarea");
        reasonInput.setId("Textarea-1764058710977");
        WeComApprovalRequest.TextareaValue reasonValue = new WeComApprovalRequest.TextareaValue();
        String subsidyReason = (reason != null ? reason : "出差补贴") + "（出差" + travelDays + "天）";
        reasonValue.setText(subsidyReason);
        reasonInput.setValue(reasonValue);
        contents.add(reasonInput);

        // 3. 提交人员
        WeComApprovalRequest.Content contact = new WeComApprovalRequest.Content();
        contact.setControl("Contact");
        contact.setId("Contact-1764058021888");
        WeComApprovalRequest.ContactValue contactValue = new WeComApprovalRequest.ContactValue();
        WeComApprovalRequest.Member member = new WeComApprovalRequest.Member();
        member.setUserid(userId);
        // 需要获取实际提交人的姓名，这里需要调用企业微信API获取用户信息
        String actualUserName = getActualUserName(userId);
        member.setName(actualUserName);
        contactValue.setMembers(Arrays.asList(member));
        contact.setValue(contactValue);
        contents.add(contact);

        // 4. 提交日期
        WeComApprovalRequest.Content date = new WeComApprovalRequest.Content();
        date.setControl("Date");
        date.setId("Date-1764058210443");
        WeComApprovalRequest.DateValue dateValue = new WeComApprovalRequest.DateValue();
        WeComApprovalRequest.Date dateObj = new WeComApprovalRequest.Date();
        dateObj.setType("day");

        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        if (applyDate != null && !applyDate.isEmpty()) {
            try {
                LocalDate applyLocalDate = LocalDate.parse(applyDate);
                timestamp = String.valueOf(applyLocalDate.atStartOfDay(ZoneId.systemDefault()).toEpochSecond());
            } catch (Exception e) {
                log.warn("解析提交日期失败，使用当前时间: {}", applyDate);
            }
        }

        dateObj.setS_timestamp(timestamp);
        dateValue.setDate(dateObj);
        date.setValue(dateValue);
        contents.add(date);

        // 5. 报销部门
        if (departmentId != null && !departmentId.trim().isEmpty() && isValidDepartmentId(departmentId)) {
            WeComApprovalRequest.Content deptContact = new WeComApprovalRequest.Content();
            deptContact.setControl("Contact");
            deptContact.setId("Contact-1764058222410");
            WeComApprovalRequest.ContactValue deptValue = new WeComApprovalRequest.ContactValue();

            WeComApprovalRequest.Member deptMember = new WeComApprovalRequest.Member();
            deptMember.setPartyid(Arrays.asList(departmentId.trim()));
            String deptName = getDepartmentNameById(departmentId);
            deptMember.setName(deptName != null ? deptName : "部门" + departmentId);
            deptValue.setMembers(Arrays.asList(deptMember));

            deptContact.setValue(deptValue);
            contents.add(deptContact);
        }

        // 6. 关联出差申请
        if (relatedApprovalNo != null && !relatedApprovalNo.trim().isEmpty()) {
            WeComApprovalRequest.Content relatedApprovalContent = new WeComApprovalRequest.Content();
            relatedApprovalContent.setControl("RelatedApproval");
            relatedApprovalContent.setId("RelatedApproval-1764058250539");

            WeComApprovalRequest.RelatedApprovalValue relatedApprovalValue = new WeComApprovalRequest.RelatedApprovalValue();
            WeComApprovalRequest.RelatedApproval relatedApproval = new WeComApprovalRequest.RelatedApproval();
            relatedApproval.setSp_no(relatedApprovalNo.trim());
            relatedApprovalValue.setRelated_approval(Arrays.asList(relatedApproval));

            relatedApprovalContent.setValue(relatedApprovalValue);
            contents.add(relatedApprovalContent);

            log.info("设置关联出差申请，审批编号: {}", relatedApprovalNo);
        }

        // 7. 报销明细表格
        WeComApprovalRequest.Content table = new WeComApprovalRequest.Content();
        table.setControl("Table");
        table.setId("item-1503317853434");
        WeComApprovalRequest.TableValue tableValue = new WeComApprovalRequest.TableValue();
        tableValue.setChildren(buildSubsidyTableData(travelStartDate, travelEndDate, travelStartPeriod,
                travelEndPeriod, travelDays, subsidyAmount));
        table.setValue(tableValue);
        contents.add(table);

        applyData.setContents(contents);
        return applyData;
    }

    /**
     * 构造补贴表格数据
     */
    private List<WeComApprovalRequest.Child> buildSubsidyTableData(String travelStartDate, String travelEndDate,
                                                                   String travelStartPeriod, String travelEndPeriod,
                                                                   String travelDays, BigDecimal subsidyAmount) {
        List<WeComApprovalRequest.Child> children = new ArrayList<>();

        WeComApprovalRequest.Child child = new WeComApprovalRequest.Child();
        List<WeComApprovalRequest.ListItem> listItems = new ArrayList<>();

        // 1. 费用类型选择器 - 固定为差旅补贴
        WeComApprovalRequest.ListItem typeItem = new WeComApprovalRequest.ListItem();
        typeItem.setControl("Selector");
        typeItem.setId("item-1503317870534");
        WeComApprovalRequest.SelectorValue typeValue = new WeComApprovalRequest.SelectorValue();
        WeComApprovalRequest.Selector selector = new WeComApprovalRequest.Selector();
        selector.setType("single");
        WeComApprovalRequest.Option option = new WeComApprovalRequest.Option();
        option.setKey("option-3085548592");
        WeComApprovalRequest.TextValue textValue = new WeComApprovalRequest.TextValue();
        textValue.setText("补助申请/差旅补贴");
        textValue.setLang("zh_CN");
        option.setValue(Arrays.asList(textValue));
        selector.setOptions(Arrays.asList(option));
        typeValue.setSelector(selector);
        typeItem.setValue(typeValue);
        listItems.add(typeItem);

        // 2. 时长（日期范围）
        if (travelStartDate != null && !travelStartDate.trim().isEmpty() &&
                travelEndDate != null && !travelEndDate.trim().isEmpty()) {

            WeComApprovalRequest.ListItem dateRangeItem = new WeComApprovalRequest.ListItem();
            dateRangeItem.setControl("DateRange");
            dateRangeItem.setId("DateRange-1764058349956");

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

            } catch (Exception e) {
                log.warn("日期转换失败，使用当前时间作为默认值", e);
                long currentTime = System.currentTimeMillis() / 1000;
                dateRange.setNew_begin(String.valueOf(currentTime - 43200));
                dateRange.setNew_end(String.valueOf(currentTime));
                dateRange.setNew_duration(43200L);
            }

            dateRangeValue.setDate_range(dateRange);
            dateRangeItem.setValue(dateRangeValue);
            listItems.add(dateRangeItem);
        }

        // 3. 金额
        WeComApprovalRequest.ListItem moneyItem = new WeComApprovalRequest.ListItem();
        moneyItem.setControl("Money");
        moneyItem.setId("item-1503317989302");
        WeComApprovalRequest.MoneyValue moneyValue = new WeComApprovalRequest.MoneyValue();
        moneyValue.setNew_money(subsidyAmount.toString());
        moneyItem.setValue(moneyValue);
        listItems.add(moneyItem);

        // 4. 消费事由
        WeComApprovalRequest.ListItem descItem = new WeComApprovalRequest.ListItem();
        descItem.setControl("Textarea");
        descItem.setId("item-1503318001306");
        WeComApprovalRequest.TextareaValue descValue = new WeComApprovalRequest.TextareaValue();
        descValue.setText("出差" + travelDays + "天补贴（" + travelStartDate + "至" + travelEndDate + "）");
        descItem.setValue(descValue);
        listItems.add(descItem);

        // 5. 附件文件控件（空）
        WeComApprovalRequest.ListItem fileItem = new WeComApprovalRequest.ListItem();
        fileItem.setControl("File");
        fileItem.setId("item-1503385054053");
        WeComApprovalRequest.FileValue fileValue = new WeComApprovalRequest.FileValue();
        fileValue.setFiles(new ArrayList<>());
        fileItem.setValue(fileValue);
        listItems.add(fileItem);

        child.setList(listItems);
        children.add(child);

        return children;
    }

    /**
     * 构造出差补贴申请单摘要信息
     */
    private List<WeComApprovalRequest.Summary> buildTravelSubsidySummaryList(String userId, String userName, String travelDays,
                                                                             BigDecimal subsidyAmount, String relatedApprovalNo) {
        List<WeComApprovalRequest.Summary> summaries = new ArrayList<>();

        // 第一行摘要：出差补贴基本信息
        WeComApprovalRequest.Summary summary1 = new WeComApprovalRequest.Summary();
        List<WeComApprovalRequest.SummaryInfo> summaryInfo1 = new ArrayList<>();
        WeComApprovalRequest.SummaryInfo info1 = new WeComApprovalRequest.SummaryInfo();

        String relatedInfo = relatedApprovalNo != null ? "，关联出差申请：" + relatedApprovalNo : "";

        // 修改这里：使用 userName 而不是 userId
        String actualUserName = getActualUserName(userId); // 获取实际用户姓名
        info1.setText("出差补贴申请 - " + actualUserName + "，出差" + travelDays + "天，补贴金额：" +
                subsidyAmount + "元" + relatedInfo);
        info1.setLang("zh_CN");
        summaryInfo1.add(info1);
        summary1.setSummary_info(summaryInfo1);
        summaries.add(summary1);

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

        return "部门" + departmentId;
    }

    /**
     * 验证部门ID格式
     */
    private boolean isValidDepartmentId(String departmentId) {
        if (departmentId == null || departmentId.trim().isEmpty()) {
            return false;
        }

        try {
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