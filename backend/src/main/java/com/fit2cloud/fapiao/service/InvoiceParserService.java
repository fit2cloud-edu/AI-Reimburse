package com.fit2cloud.fapiao.service;

import com.fit2cloud.fapiao.dto.response.InvoiceInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class InvoiceParserService {

    /**
     * 从智能体返回的内容中解析发票信息和media_ids
     * @param content 智能体返回的文本内容
     * @return 包含发票信息和mediaIds的解析结果
     */
    public InvoiceParseResult parseInvoicesFromContent(String content) {
        List<InvoiceInfo> invoices = new ArrayList<>();
        String mediaIds = null;

        if (content == null || content.isEmpty()) {
            return new InvoiceParseResult(invoices, mediaIds);
        }

        try {
            // 首先提取media_ids
            mediaIds = extractMediaIds(content);

            // 查找"### 报销详细信息"到"### 总计"之间的内容
            String invoiceSection = extractInvoiceSection(content);

            if (invoiceSection == null || invoiceSection.isEmpty()) {
                log.warn("未找到有效的发票信息部分");
                return new InvoiceParseResult(invoices, mediaIds);
            }

            // 分割发票信息
            List<String> invoiceParts = splitInvoiceSections(invoiceSection);

            // 解析每个发票部分
            for (String part : invoiceParts) {
                InvoiceInfo invoice = parseSingleInvoice(part);
                if (invoice != null) {
                    invoices.add(invoice);
                }
            }

            log.info("成功解析出 {} 张发票信息, mediaIds: {}", invoices.size(), mediaIds);
            return new InvoiceParseResult(invoices, mediaIds);

        } catch (Exception e) {
            log.error("解析发票信息时发生错误", e);
            return new InvoiceParseResult(new ArrayList<>(), null);
        }
    }

    /**
     * 提取media_ids
     */
    private String extractMediaIds(String content) {
        // 匹配 media_ids: 后面的内容，支持逗号分隔的多个mediaId
        // 匹配模式：media_ids: 后面跟着字母、数字、下划线、连字符和逗号
        Pattern pattern = Pattern.compile("media_ids:\\s*([a-zA-Z0-9_,-]+)");
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            String mediaIds = matcher.group(1).trim();
            // 清理可能的空格
            mediaIds = mediaIds.replaceAll("\\s+", "");
            log.info("提取到media_ids: {}", mediaIds);
            return mediaIds;
        }

        log.warn("未找到media_ids");
        return null;
    }

    /**
     * 提取发票信息部分
     */
    private String extractInvoiceSection(String content) {
        // 查找"### 报销详细信息"开始的位置
        int startIndex = content.indexOf("### 报销详细信息");
        if (startIndex < 0) {
            return null;
        }

        // 查找"### 总计"结束的位置
        int endIndex = content.indexOf("### 总计");
        if (endIndex < 0) {
            // 如果没有总计，取到内容末尾
            endIndex = content.length();
        }

        return content.substring(startIndex, endIndex);
    }

    /**
     * 分割发票区域
     */
    private List<String> splitInvoiceSections(String invoiceSection) {
        List<String> sections = new ArrayList<>();

        // 按照分隔符分割发票
        String[] parts = invoiceSection.split("\\s*-+\\s*");

        // 处理分割后的部分
        for (String part : parts) {
            String trimmedPart = part.trim();
            // 只保留包含发票关键信息的部分
            if (trimmedPart.contains("发票项目名称") && trimmedPart.contains("发票总金额")) {
                sections.add(trimmedPart);
            }
        }

        // 如果按分隔符分割失败，尝试按"发票项目名称"分割
        if (sections.isEmpty()) {
            // 使用正则表达式查找每个发票块
            Pattern pattern = Pattern.compile("(-\\s*发票项目名称.*?)(?=\\n-\\s*发票项目名称|\\s*$)", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(invoiceSection);

            while (matcher.find()) {
                String block = matcher.group(1).trim();
                if (!block.isEmpty()) {
                    sections.add(block);
                }
            }
        }

        return sections;
    }

    /**
     * 解析单张发票信息
     * @param section 发票文本段落
     * @return 发票信息对象
     */
    private InvoiceInfo parseSingleInvoice(String section) {
        try {
            InvoiceInfo invoice = new InvoiceInfo();

            // 使用更宽松的正则表达式提取各项信息
            invoice.setInvoiceItemName(extractValue(section, "发票项目名称"));
            invoice.setTotalAmount(extractValue(section, "发票总金额"));
            invoice.setBuyerName(extractValue(section, "购买方名称"));
            invoice.setBuyerCode(extractValue(section, "购买方代码"));
            invoice.setSellerName(extractValue(section, "销售方名称"));
            invoice.setInvoiceNumber(extractValue(section, "发票号码"));
            invoice.setInvoiceDate(extractValue(section, "开票日期"));
            invoice.setHasSeal(extractValue(section, "是否有印章"));

            // 修改：优先提取"发票备注"，如果没有则提取"备注"
            String invoiceRemark = extractValue(section, "发票备注");
            if (invoiceRemark == null) {
                invoiceRemark = extractValue(section, "备注");
            }
            invoice.setRemark(invoiceRemark);
            invoice.setInvoiceRemark(invoiceRemark); // 专门存储备注信息

            // 只返回费用类型文本，不进行key映射
            invoice.setReimbursementType(extractValue(section, "报销类型"));
            invoice.setReimbursementReason(extractValue(section, "报销事由"));

            // 提取合规检查信息
            String complianceCheck = extractComplianceCheck(section);
            invoice.setComplianceCheck(complianceCheck);
            log.info("提取到合规检查信息: {}", complianceCheck);

            // 验证是否为有效发票记录（至少要有发票项目名称和总金额）
            if (invoice.getInvoiceItemName() != null && invoice.getTotalAmount() != null) {
                log.debug("成功解析发票: {} - {}, 备注: {}, 合规检查: {}",
                        invoice.getInvoiceItemName(),
                        invoice.getTotalAmount(),
                        invoice.getRemark(),
                        invoice.getComplianceCheck());
                return invoice;
            }

            return null;
        } catch (Exception e) {
            log.error("解析单张发票信息时发生错误", e);
            return null;
        }
    }

    /**
     * 提取合规检查信息
     * 从发票文本中提取"合规检查"字段
     */
    private String extractComplianceCheck(String text) {
        // 多种匹配模式，适应不同格式
        String[] patterns = {
                "-\\s*合规检查\\s*[:：]\\s*(.*?)(?:\\n|$)",      // 模式1: - 合规检查: xxx
                "合规检查\\s*[:：]\\s*(.*?)(?:\\n|$)",           // 模式2: 合规检查: xxx
                "合规[：:]\\s*(.*?)(?:\\n|$)",                  // 模式3: 合规: xxx
                "标准[：:]\\s*(.*?)(?:\\n|$)"                   // 模式4: 标准: xxx
        };

        for (String patternStr : patterns) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(text);

            if (matcher.find()) {
                String value = matcher.group(1).trim();

                // 处理特殊字符和空值
                if (value == null || value.isEmpty() || "（空）".equals(value)) {
                    return null;
                }

                // 清理常见的描述性文字
                value = cleanComplianceCheckValue(value);

                log.debug("使用模式 '{}' 提取到合规检查信息: {}", patternStr, value);
                return value;
            }
        }

        // 如果未找到明确的合规检查字段，尝试从备注或项目名称推断
        return inferComplianceCheckFromContext(text);
    }

    /**
     * 清理合规检查值
     * 移除多余的描述性文字，保留核心信息
     */
    private String cleanComplianceCheckValue(String value) {
        if (value == null) return null;

        // 移除常见的描述性前缀/后缀
        value = value.replaceAll("^为?\\s*", "");  // 移除开头的"为"
        value = value.replaceAll("\\s*标准?$", ""); // 移除结尾的"标准"
        value = value.replaceAll("^标准[：:]\\s*", ""); // 移除开头的"标准:"

        // 常见合规检查值的标准化
        if (value.contains("高铁") || value.contains("动车") || value.contains("火车")) {
            // 提取座位类型
            if (value.contains("一等座")) {
                return "高铁-一等座";
            } else if (value.contains("二等座")) {
                return "高铁-二等座";
            } else if (value.contains("动卧")) {
                return "火车-动卧";
            } else if (value.contains("卧铺")) {
                return "火车-卧铺";
            } else {
                return "高铁-二等座"; // 默认
            }
        } else if (value.contains("飞机") || value.contains("航班")) {
            if (value.contains("经济舱")) {
                return "飞机-经济舱";
            } else if (value.contains("商务舱") || value.contains("头等舱")) {
                return "飞机-商务舱";
            } else {
                return "飞机-经济舱"; // 默认
            }
        } else if (value.contains("酒店") || value.contains("住宿")) {
            // 常见酒店品牌匹配
            if (value.contains("汉庭")) {
                return "华住-汉庭";
            } else if (value.contains("宜必思")) {
                return "华住-宜必思";
            } else if (value.contains("你好酒店")) {
                return "华住-你好酒店";
            } else if (value.contains("怡莱")) {
                return "华住-怡莱酒店";
            } else if (value.contains("华住")) {
                return "华住-其他";
            }
        }

        return value.trim();
    }

    /**
     * 从上下文推断合规检查信息
     * 当没有明确的合规检查字段时，尝试从其他字段推断
     */
    private String inferComplianceCheckFromContext(String text) {
        // 1. 从发票项目名称推断
        String invoiceItemName = extractValue(text, "发票项目名称");
        String remark = extractValue(text, "备注");

        // 检查是否为交通相关发票
        if (invoiceItemName != null) {
            String lowerItemName = invoiceItemName.toLowerCase();

            // 交通相关
            if (lowerItemName.contains("高铁") || lowerItemName.contains("动车") ||
                    lowerItemName.contains("火车") || lowerItemName.contains("车票")) {

                // 从备注中提取座位信息
                if (remark != null) {
                    if (remark.contains("一等座")) return "高铁-一等座";
                    if (remark.contains("二等座")) return "高铁-二等座";
                    if (remark.contains("动卧")) return "火车-动卧";
                }

                // 默认返回二等座
                return "高铁-二等座";
            }

            // 飞机相关
            else if (lowerItemName.contains("飞机") || lowerItemName.contains("机票") ||
                    lowerItemName.contains("航空")) {

                if (remark != null) {
                    if (remark.contains("经济舱")) return "飞机-经济舱";
                    if (remark.contains("商务舱") || remark.contains("头等舱")) return "飞机-商务舱";
                }

                return "飞机-经济舱";
            }

            // 住宿相关
            else if (lowerItemName.contains("住宿") || lowerItemName.contains("酒店") ||
                    lowerItemName.contains("宾馆")) {

                if (remark != null) {
                    if (remark.contains("汉庭")) return "华住-汉庭";
                    if (remark.contains("宜必思")) return "华住-宜必思";
                    if (remark.contains("你好")) return "华住-你好酒店";
                    if (remark.contains("怡莱")) return "华住-怡莱酒店";
                }

                return "华住-其他";
            }
        }

        return null;
    }

    /**
     * 从文本中提取指定字段的值
     * @param text 文本内容
     * @param fieldName 字段名称
     * @return 字段值
     */
    private String extractValue(String text, String fieldName) {
        // 更宽松的匹配模式，适应不同的空格和格式
        String pattern = "-\\s*" + fieldName + "\\s*[:：]\\s*(.*?)(?:\\n|$)";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(text);

        if (m.find()) {
            String value = m.group(1).trim();
            // 如果值为"（空）"或为空则返回null
            if ("（空）".equals(value) || value.isEmpty()) {
                return null;
            }
            return value;
        }

        return null;
    }

    /**
     * 发票解析结果类
     */
    public static class InvoiceParseResult {
        private List<InvoiceInfo> invoices;
        private String mediaIds;

        public InvoiceParseResult(List<InvoiceInfo> invoices, String mediaIds) {
            this.invoices = invoices;
            this.mediaIds = mediaIds;
        }

        public List<InvoiceInfo> getInvoices() {
            return invoices;
        }

        public String getMediaIds() {
            return mediaIds;
        }
    }
}