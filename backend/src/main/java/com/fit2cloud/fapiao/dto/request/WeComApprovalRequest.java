// #file src/main/java/com/fit2cloud/fapiao/dto/request/WeComApprovalRequest.java
package com.fit2cloud.fapiao.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Data
public class WeComApprovalRequest {
    private String creator_userid;
    private String template_id;
    private int use_template_approver;
    private List<Approver> approver;
    private List<String> notifyer;
    private int notify_type;
    private ApplyData apply_data;
    private List<Summary> summary_list;

    @Data
    public static class Approver {
        private int attr;
        private List<String> userid;
    }

    @Data
    public static class ApplyData {
        private List<Content> contents;
    }

    @Data
    public static class Content {
        private String control;
        private String id;
        private Object value;
    }

    @Data
    public static class Summary {
        private List<SummaryInfo> summary_info;
    }

    @Data
    public static class SummaryInfo {
        private String text;
        private String lang;
    }

    // Value types for different controls
    @Data
    public static class SelectorValue {
        private Selector selector;
    }

    @Data
    public static class Selector {
        private String type;
        private List<Option> options;
    }

    @Data
    public static class Option {
        private String key;
        private List<TextValue> value;
    }

    @Data
    public static class TextValue {
        private String text;
        private String lang;
    }

    @Data
    public static class ContactValue {
        private List<Member> members;
    }

    @Data
    public static class Member {
        private String userid;  // 字符串类型，可以为null
        private String name;
        private List<String> partyid; // 部门ID列表

        // getter 和 setter 方法
        public void setUserid(String userid) { this.userid = userid; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public List<String> getPartyid() { return partyid; }
        public void setPartyid(List<String> partyid) { this.partyid = partyid; }

        // 添加Jackson注解来控制序列化
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getUserid() { return userid; }
    }

    @Data
    public static class DateValue {
        private Date date;
    }

    @Data
    public static class Date {
        private String type;
        private String s_timestamp;
    }

    @Data
    public static class TextareaValue {
        private String text;
    }

    @Data
    public static class TableValue {
        private List<Child> children;
    }

    @Data
    public static class Child {
        private List<ListItem> list;
    }

    @Data
    public static class ListItem {
        private String control;
        private String id;
        private Object value;
    }

    @Data
    public static class MoneyValue {
        private String new_money;
    }

    @Data
    public static class FileValue {
        private List<File> files;
    }

    @Data
    public static class File {
        private String file_id;
    }

    /**
     * DateRange 控件值
     */
    @Data
    public static class DateRangeValue {
        private DateRange date_range;
    }

    /**
     * DateRange 配置
     */
    @Data
    public static class DateRange {
        private String type;           // 时间类型：halfday(半天)
        private String new_begin; // 开始时间戳
        private String new_end;   // 结束时间戳
        private Long new_duration; // 时长范围（秒）


        private String s_timestamp;    // 开始时间戳
        private String e_timestamp;    // 结束时间戳
        private Integer s_halfday;     // 开始时间段（1:上午/2:下午）
        private Integer e_halfday;     // 结束时间段（1:上午/2:下午）
        private Integer official_holiday = 0; // 是否包含节假日
        private Integer perday_duration = 86400;  // 每天时长（秒）
    }

    /**
     * 关联审批单控件值
     */
    @Data
    public static class RelatedApprovalValue {
        private List<RelatedApproval> related_approval;
    }

    /**
     * 关联审批单
     */
    @Data
    public static class RelatedApproval {
        private String sp_no; // 关联审批单的审批单号
    }
}
