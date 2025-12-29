package com.fit2cloud.fapiao.controller;

import com.fit2cloud.fapiao.dto.response.ApiResponse;
import com.fit2cloud.fapiao.dto.response.QyLoginResult;
import com.fit2cloud.fapiao.service.QyWechatWebService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 企业微信Web端登录控制器
 * 专门处理Web端的企业微信登录，与小程序登录完全分离
 */
@Slf4j
@RestController
@RequestMapping("/qywechat/web")
public class QyWechatWebController {

    private final QyWechatWebService qyWechatWebService;

    public QyWechatWebController(QyWechatWebService qyWechatWebService) {
        this.qyWechatWebService = qyWechatWebService;
    }

    /**
     * Web端企业微信登录接口
     * 路径：POST /qywechat/web/login
     */
    @PostMapping("/login")
    public ApiResponse<QyLoginResult> webLogin(@RequestBody WebLoginRequest request) {
        try {
            log.info("收到Web端企业微信登录请求, code长度: {}",
                    request.getCode() != null ? request.getCode().length() : 0);

            // 调用专门的Web端服务
            QyLoginResult result = qyWechatWebService.getWebUserInfoByCode(request.getCode());

            log.info("Web端企业微信登录完成, userid: {}, name: {}",
                    result.getUserId(), result.getUserName());

            return ApiResponse.success("Web端登录成功", result);

        } catch (Exception e) {
            log.error("Web端企业微信登录失败", e);
            return ApiResponse.error("Web端登录失败: " + e.getMessage());
        }
    }

    /**
     * Web端session验证接口
     * 路径：POST /qywechat/web/check-session
     */
    @PostMapping("/check-session")
    public ApiResponse<Boolean> checkWebSession(@RequestBody CheckWebSessionRequest request) {
        try {
            boolean isValid = qyWechatWebService.validateWebSession(request.getSessionKey());
            log.info("Web端session验证结果: {}", isValid);
            return ApiResponse.success(isValid);
        } catch (Exception e) {
            log.error("Web端session验证失败", e);
            return ApiResponse.error("Web端session验证失败: " + e.getMessage());
        }
    }

    /**
     * Web端健康检查接口
     * 路径：GET /qywechat/web/health
     */
    @GetMapping("/health")
    public ApiResponse<String> webHealthCheck() {
        return ApiResponse.success("企业微信Web端服务运行正常");
    }

    /**
     * 生成JS-SDK签名接口
     * 路径：POST /qywechat/web/get-signature
     */
    @PostMapping("/get-signature")
    public ApiResponse<Map<String, String>> getJsSdkSignature(@RequestBody GetSignatureRequest request) {
        try {
            log.info("收到JS-SDK签名请求, url: {}", request.getUrl());

            Map<String, String> signature = qyWechatWebService.generateJsSdkSignature(request.getUrl());

            log.info("JS-SDK签名生成成功, timestamp: {}, nonceStr: {}",
                    signature.get("timestamp"), signature.get("nonceStr"));

            return ApiResponse.success("签名生成成功", signature);

        } catch (Exception e) {
            log.error("JS-SDK签名生成失败", e);
            return ApiResponse.error("签名生成失败: " + e.getMessage());
        }
    }

    // Web端专用请求参数类
    public static class WebLoginRequest {
        private String code;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    public static class CheckWebSessionRequest {
        private String sessionKey;

        public String getSessionKey() { return sessionKey; }
        public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }
    }

    public static class GetSignatureRequest {
        private String url;

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
}
