package com.fit2cloud.fapiao.controller;

import com.fit2cloud.fapiao.dto.response.ApiResponse;
import com.fit2cloud.fapiao.dto.response.FileUploadResponse;
import com.fit2cloud.fapiao.exception.BusinessException;
import com.fit2cloud.fapiao.service.FileUploadService;
import com.fit2cloud.fapiao.service.WeChatWorkFileService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/upload")
@Slf4j
public class FileUploadController {

    @Autowired
    private FileUploadService fileUploadService;

    @Autowired
    private WeChatWorkFileService weChatWorkFileService;


    // 用于存储上传队列中的文件信息
    private static final Map<String, List<UploadedFile>> uploadQueue = new ConcurrentHashMap<>();
    private static final Map<String, Long> uploadTimestamps = new ConcurrentHashMap<>();
    private static final Map<String, String> sessionFormTypes = new ConcurrentHashMap<>();

    // 内部类用于存储文件内容和元数据
    private static class UploadedFile {
        private final byte[] content;
        private final String originalFilename;
        private final String contentType;

        public UploadedFile(MultipartFile file) throws IOException {
            this.content = file.getBytes();
            this.originalFilename = file.getOriginalFilename();
            this.contentType = file.getContentType();
        }

        public byte[] getContent() {
            return content;
        }

        public String getOriginalFilename() {
            return originalFilename;
        }

        public String getContentType() {
            return contentType;
        }
    }

    /**
     * 逐个上传文件并存储到队列中 - 修改返回格式匹配前端
     */
    @PostMapping("/invoice/single")
    public ApiResponse<FileUploadResponse> uploadSingleInvoice(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sessionId") String sessionId,
            @RequestParam(value = "isLast") boolean isLast,
            @RequestParam(value = "formType", required = false) String formType) {

        try {
            log.info("收到单个发票上传请求, 会话ID: {}, 是否为最后一个文件: {}, 表单类型: {}", sessionId, isLast, formType);

            // 添加文件验证
            if (file == null || file.isEmpty()) {
                log.warn("上传的文件为空");
                return ApiResponse.error("上传的文件不能为空");
            }

            // 验证文件是否有效
            try {
                file.getInputStream(); // 测试文件是否可读
            } catch (Exception e) {
                log.warn("文件读取异常: {}", e.getMessage());
                return ApiResponse.error("文件读取异常");
            }

            // 将文件内容读取并存储到上传队列中，避免临时文件被清理
            UploadedFile uploadedFile = new UploadedFile(file);
            uploadQueue.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(uploadedFile);
            uploadTimestamps.put(sessionId, System.currentTimeMillis());

            log.info("文件添加到上传队列成功, 当前会话文件数量: {}",
                    uploadQueue.get(sessionId).size());

            // 存储formType到session
            if (formType != null && !formType.isEmpty()) {
               sessionFormTypes.put(sessionId, formType);
            }

            if (isLast) {
                // 如果是最后一个文件，处理队列中的所有文件并返回结果
                return processQueuedFiles(sessionId);
            }

            // 返回成功的响应，但不包含发票信息
            FileUploadResponse response = new FileUploadResponse();
            response.setSuccess(true);
            response.setMessage("文件上传成功，等待后续处理");
            return ApiResponse.success(response);

        } catch (BusinessException e) {
            log.warn("业务处理异常: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("发票上传识别系统异常", e);
            return ApiResponse.error("系统处理异常，请稍后重试");
        }
    }

    /**
     * 处理队列中的所有文件 - 修改返回格式
     */
    private ApiResponse<FileUploadResponse> processQueuedFiles(String sessionId) {
        List<UploadedFile> files = uploadQueue.remove(sessionId);
        uploadTimestamps.remove(sessionId);
        String formType = "日常报销单";//默认值

        // 获取formType
        List<?> typeList = uploadQueue.remove(sessionId + "_formType");
        if (typeList != null && !typeList.isEmpty()) {
            formType = typeList.get(0).toString();
        }

        if (files == null || files.isEmpty()) {
            log.warn("会话ID {} 对应的文件队列为空", sessionId);
            return ApiResponse.error("没有找到待处理的文件");
        }

        try {
            log.info("开始处理队列中的 {} 个文件", files.size());

            // 将 UploadedFile 转换为 MultipartFile 数组
            MultipartFile[] multipartFiles = files.stream()
                    .map(this::convertToMultipartFile)
                    .toArray(MultipartFile[]::new);

            FileUploadResponse result = fileUploadService.processMultipleFileUpload(
                    multipartFiles, "发票", formType);

            // 确保返回格式正确
            result.setSuccess(true);
            result.setMessage("文件处理完成");

            log.info("发票上传识别完成, 解析到 {} 张发票, 文件ID: {}",
                    result.getInvoiceInfos() != null ? result.getInvoiceInfos().size() : 0,
                    result.getFileId());

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("处理队列文件时发生异常", e);
            return ApiResponse.error("文件处理异常: " + e.getMessage());
        }
    }

    /**
     * 将 UploadedFile 转换为 MultipartFile
     */
    private MultipartFile convertToMultipartFile(UploadedFile uploadedFile) {
        return new InMemoryMultipartFile(
                uploadedFile.getOriginalFilename(),
                uploadedFile.getOriginalFilename(),
                uploadedFile.getContentType(),
                uploadedFile.getContent()
        );
    }

    /**
     * 批量处理接口 - 修改路径匹配前端
     */
    @PostMapping("/invoice/process")
    public ApiResponse<FileUploadResponse> processFiles(@RequestBody ProcessRequest request) {
        try {
            log.info("收到批量处理请求, 会话ID: {}", request.getSessionId());

            // 从队列中获取文件
            List<UploadedFile> files = uploadQueue.get(request.getSessionId());
            if (files == null || files.isEmpty()) {
                return ApiResponse.error("没有找到待处理的文件");
            }

            // 处理文件
            MultipartFile[] multipartFiles = files.stream()
                    .map(this::convertToMultipartFile)
                    .toArray(MultipartFile[]::new);

            FileUploadResponse result = fileUploadService.processMultipleFileUpload(
                    multipartFiles, request.getMessage(), "日常报销单");

            // 清理队列
            uploadQueue.remove(request.getSessionId());
            uploadTimestamps.remove(request.getSessionId());

            result.setSuccess(true);
            result.setMessage("批量处理完成");

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("批量处理异常", e);
            return ApiResponse.error("批量处理失败: " + e.getMessage());
        }
    }

    /**
     * 上传文件并调用智能体识别（保持原有接口不变）
     */
    @PostMapping("/invoice")
    public ApiResponse<FileUploadResponse> uploadInvoice(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "message", required = false, defaultValue = "发票") String message,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "formType", required = false, defaultValue = "日常报销单") String formType) {

        try {
            log.info("收到发票上传请求, 用户: {}, 文件数量: {}, 消息: {}",
                    userId, files.length, formType);

            FileUploadResponse result = fileUploadService.processMultipleFileUpload(files, message, formType);
            result.setSuccess(true);
            result.setMessage("发票识别完成");

            log.info("发票上传识别完成, 解析到 {} 张发票",
                    result.getInvoiceInfos() != null ? result.getInvoiceInfos().size() : 0);
            return ApiResponse.success(result);

        } catch (BusinessException e) {
            log.warn("业务处理异常: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("发票上传识别系统异常", e);
            return ApiResponse.error("系统处理异常，请稍后重试");
        }
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ApiResponse<String> healthCheck() {
        return ApiResponse.success("文件上传服务运行正常");
    }

    // 请求参数类
    public static class ProcessRequest {
        private String sessionId;
        private String message;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    @PostMapping("/wedrive")
    public ApiResponse<FileUploadResponse> uploadWedriveFiles(
            @RequestBody WedriveUploadRequest request) {
        try {
            log.info("收到微盘文件上传请求, sessionId: {}, ticket数量: {}",
                    request.getSessionId(), request.getTickets().size());

            // 将ticket转换为文件
            List<MultipartFile> files = new ArrayList<>();
            for (int i = 0; i < request.getTickets().size(); i++) {
                String ticket = request.getTickets().get(i);
                byte[] fileContent = weChatWorkFileService.downloadFileFromWeDrive(ticket);

                // 生成文件名
                String filename = "wedrive_file_" + System.currentTimeMillis() + "_" + i;
                if (fileContent.length > 0) {
                    // 尝试从文件头判断类型（简化版）
                    if (fileContent[0] == (byte)0xFF && fileContent[1] == (byte)0xD8) {
                        filename += ".jpg";
                    } else if (fileContent[0] == (byte)0x25 && fileContent[1] == (byte)0x50) {
                        filename += ".pdf";
                    } else {
                        filename += ".pdf"; // 默认
                    }
                }

                files.add(new WedriveFileAdapter(fileContent, filename));
            }

            // 转换为数组并调用现有处理逻辑
            MultipartFile[] fileArray = files.toArray(new MultipartFile[0]);
            String formType = request.getMessage() != null && request.getMessage().contains("客成差旅") ? "客成差旅报销单" : "日常报销单";
            FileUploadResponse result = fileUploadService.processMultipleFileUpload(
                    fileArray, request.getMessage(), formType);

            result.setSuccess(true);
            result.setMessage("微盘文件处理完成");

            return ApiResponse.success(result);

        } catch (BusinessException e) {
            log.warn("微盘文件业务处理异常: {}", e.getMessage());
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("微盘文件处理系统异常", e);
            return ApiResponse.error("微盘文件处理异常: " + e.getMessage());
        }
    }

    // 请求DTO
    @Data
    public static class WedriveUploadRequest {
        private String sessionId;
        private List<String> tickets;
        private String message;
    }
}