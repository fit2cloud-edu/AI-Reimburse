package com.fit2cloud.fapiao.service;

import com.fit2cloud.fapiao.dto.request.ChatMessageRequest;
import com.fit2cloud.fapiao.dto.request.ImageInfo;
import com.fit2cloud.fapiao.dto.response.*;
import com.fit2cloud.fapiao.util.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class FileUploadService {

    @Autowired
    private OSSService ossService;

    @Autowired
    private MaxKBService maxkbService;

    @Autowired
    private FileUtil fileUtil;

    @Autowired
    private InvoiceParserService invoiceParserService;

    @Autowired
    private RuleValidationService ruleValidationService;

    /**
     * 处理单个文件上传和智能体识别的完整流程
     */
    public FileUploadResponse processFileUpload(MultipartFile file, String message, String formType) {
        try {
            // 1. 验证文件
            fileUtil.validateFile(file);

            // 2. 上传文件到OSS获取file_id
            OSSUploadResponse ossResponse = ossService.uploadFileToOSS(file);

            // 从返回的data中提取纯UUID
            String fileIdWithPrefix = ossResponse.getData().getFileIdentifier();
            String fileId = extractPureUUID(fileIdWithPrefix);
            String fileName = file.getOriginalFilename();

            // 验证fileId是否为有效的UUID格式
            if (!isValidUUID(fileId)) {
                throw new RuntimeException("文件ID格式无效: " + fileIdWithPrefix);
            }

            log.info("文件OSS上传完成, fileId: {}, fileName: {}", fileId, fileName);

            // 构建业务上下文消息
            String enhancedMessage = buildEnhancedMessage(message, formType);

            // 3. 获取会话ID
            String chatId = maxkbService.getChatId();
            log.info("获取会话ID成功: {}", chatId);

            // 4. 构建文件信息列表
            String fileUrl = "./oss/file/" + fileId;
            List<ImageInfo> imageList = new ArrayList<>();
            List<ImageInfo> documentList = new ArrayList<>();

            // 根据文件类型决定添加到图片列表还是文档列表
            if (isImageFile(fileName)) {
                imageList.add(new ImageInfo(fileName, fileUrl, fileId));
            } else {
                documentList.add(new ImageInfo(fileName, fileUrl, fileId));
            }

            // 5. 构建对话请求
            ChatMessageRequest chatRequest = new ChatMessageRequest(message,imageList,documentList,formType);
            chatRequest.setMessage(message);
            chatRequest.setStream(false);
            chatRequest.setRe_chat(false);
            chatRequest.setImage_list(imageList);
            chatRequest.setDocument_list(documentList);
            chatRequest.setBusinessContext(formType);

            // 6. 调用智能体对话接口
            ChatMessageResponse chatResponse = maxkbService.sendChatMessage(chatId, chatRequest);

            log.info("智能体识别完成, fileId: {}", fileId);

            // 7. 解析发票信息和mediaIds
            InvoiceParserService.InvoiceParseResult parseResult = invoiceParserService.parseInvoicesFromContent(chatResponse.getData().getContent());

            // 8. 构建响应
            FileUploadResponse response = new FileUploadResponse(
                    fileId, fileName, chatResponse.getData().getContent()
            );
            response.setInvoiceInfos(parseResult.getInvoices());
            response.setMediaIds(parseResult.getMediaIds()); // 设置mediaIds

            // 确保返回对象包含必要的字段
            response.setSuccess(true);
            response.setMessage("处理完成");

            log.info("文件处理完成, 解析到 {} 张发票, mediaIds: {}",
                    parseResult.getInvoices().size(), parseResult.getMediaIds());

            return response;

        } catch (Exception e) {
            log.error("文件上传处理异常", e);
            throw new RuntimeException("文件上传处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理多个文件上传和智能体识别的完整流程（支持图片和文档混合）
     */
    public FileUploadResponse processMultipleFileUpload(MultipartFile[] files, String message, String formType) {
        try {
            List<ImageInfo> imageList = new ArrayList<>();
            List<ImageInfo> documentList = new ArrayList<>();
            String primaryFileId = null;
            String primaryFileName = null;

            // 1. 验证并上传所有文件
            for (int i = 0; i < files.length; i++) {
                MultipartFile file = files[i];

                // 验证文件
                fileUtil.validateFile(file);

                // 上传文件到OSS获取file_id
                OSSUploadResponse ossResponse = ossService.uploadFileToOSS(file);

                // 从返回的data中提取纯UUID
                String fileIdWithPrefix = ossResponse.getData().getFileIdentifier();
                String fileId = extractPureUUID(fileIdWithPrefix);
                String fileName = file.getOriginalFilename();

                // 验证fileId是否为有效的UUID格式
                if (!isValidUUID(fileId)) {
                    throw new RuntimeException("文件ID格式无效: " + fileIdWithPrefix);
                }

                log.info("文件OSS上传完成, fileId: {}, fileName: {}", fileId, fileName);

                // 保存第一个文件的信息作为主文件
                if (i == 0) {
                    primaryFileId = fileId;
                    primaryFileName = fileName;
                }

                // 构建文件信息列表，根据文件类型分类
                String fileUrl = "./oss/file/" + fileId;
                if (isImageFile(fileName)) {
                    imageList.add(new ImageInfo(fileName, fileUrl, fileId));
                } else {
                    documentList.add(new ImageInfo(fileName, fileUrl, fileId));
                }
            }

            // 构建业务上下文消息
            String enhancedMessage = buildEnhancedMessage(message, formType);

            // 2. 获取会话ID
            String chatId = maxkbService.getChatId();
            log.info("获取会话ID成功: {}", chatId);

            // 3. 构建对话请求
            ChatMessageRequest chatRequest = new ChatMessageRequest(message, imageList, documentList,formType);
            chatRequest.setMessage(message);
            chatRequest.setStream(false);
            chatRequest.setRe_chat(false);
            chatRequest.setImage_list(imageList);
            chatRequest.setDocument_list(documentList);
            chatRequest.setBusinessContext(enhancedMessage);// 添加业务上下文消息

            // 4. 调用智能体对话接口
            ChatMessageResponse chatResponse = maxkbService.sendChatMessage(chatId, chatRequest);

            log.info("智能体识别完成, 主文件 fileId: {}", primaryFileId);

//            // 5. 解析发票信息和mediaIds
//            InvoiceParserService.InvoiceParseResult parseResult = invoiceParserService.parseInvoicesFromContent(chatResponse.getData().getContent());
//
//            // 添加调试日志
//            log.info("智能体返回的完整内容: {}", chatResponse.getData().getContent());
//            log.info("解析后的mediaIds: {}", parseResult.getMediaIds());
//            log.info("解析后的发票数量: {}", parseResult.getInvoices().size());
//
//            // 6. 构建响应并解析发票信息（使用第一个文件的信息作为主信息）
//            FileUploadResponse response = new FileUploadResponse(
//                    primaryFileId, primaryFileName, chatResponse.getData().getContent()
//            );
//            response.setInvoiceInfos(parseResult.getInvoices());
//            response.setMediaIds(parseResult.getMediaIds()); // 设置mediaIds
//
//            // 确保返回对象包含必要的字段
//            response.setSuccess(true);
//            response.setMessage("处理完成");
//
//            log.info("多文件处理完成, 解析到 {} 张发票, mediaIds: {}",
//                    parseResult.getInvoices().size(), parseResult.getMediaIds());
//
//            return response;

            // 7. 解析发票信息和mediaIds
            InvoiceParserService.InvoiceParseResult parseResult = invoiceParserService.parseInvoicesFromContent(chatResponse.getData().getContent());

            // 新增：规则校验
            BatchValidationResult validationResult = ruleValidationService.validateInvoices(parseResult.getInvoices(), formType);

            // 8. 构建响应
            FileUploadResponse response = new FileUploadResponse(
                    primaryFileId, primaryFileName, chatResponse.getData().getContent()
            );
            response.setInvoiceInfos(parseResult.getInvoices());
            response.setMediaIds(parseResult.getMediaIds());
            response.setValidationResult(validationResult); // 设置校验结果

            return response;

        } catch (Exception e) {
            log.error("多文件上传处理异常", e);
            throw new RuntimeException("多文件上传处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建增强的消息提示
     */
    private String buildEnhancedMessage(String originalMessage, String formType) {
        String context = "【报销类型：" + formType + "】\n";
        context += "请识别发票内容，并按照以下格式返回：\n";
        context += "1. 首先输出报销类型\n";
        context += "2. 然后输出每张发票的费用类型、金额、日期\n";
        context += "3. 最后输出总计金额\n";
        context += "请确保信息准确完整。";

        return context + originalMessage;
    }

    /**
     * 判断文件是否为图片类型
     */
    private boolean isImageFile(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerFileName = fileName.toLowerCase();
        return lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg") ||
                lowerFileName.endsWith(".png") || lowerFileName.endsWith(".gif") ||
                lowerFileName.endsWith(".bmp") || lowerFileName.endsWith(".webp");
    }

    /**
     * 从可能包含路径的字符串中提取纯UUID
     */
    private String extractPureUUID(String fileIdWithPrefix) {
        if (fileIdWithPrefix == null) {
            return null;
        }

        // 如果包含路径信息，提取最后的UUID部分
        if (fileIdWithPrefix.contains("/")) {
            String[] parts = fileIdWithPrefix.split("/");
            return parts[parts.length - 1];
        }

        return fileIdWithPrefix;
    }

    /**
     * 验证字符串是否为有效的UUID格式
     */
    private boolean isValidUUID(String uuidString) {
        if (uuidString == null || uuidString.isEmpty()) {
            return false;
        }
        try {
            UUID.fromString(uuidString);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}