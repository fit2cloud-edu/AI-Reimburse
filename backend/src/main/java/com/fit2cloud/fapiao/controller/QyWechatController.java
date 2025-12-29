package com.fit2cloud.fapiao.controller;

import com.fit2cloud.fapiao.dto.response.ApiResponse;
import com.fit2cloud.fapiao.dto.response.QyLoginResult;
import com.fit2cloud.fapiao.service.QyWechatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/qywechat")
public class QyWechatController {

    private final QyWechatService qyWechatService;

    public QyWechatController(QyWechatService qyWechatService) {
        this.qyWechatService = qyWechatService;
    }

    /**
     * 企业微信登录接口
     */
    @PostMapping("/login")
    public ApiResponse<QyLoginResult> login(@RequestBody LoginRequest request) {
        try {
            log.info("收到企业微信登录请求, code长度: {}",
                    request.getCode() != null ? request.getCode().length() : 0);

            QyLoginResult result = qyWechatService.getUserInfoByCode(request.getCode());

            //  返回前打印关键日志
            log.info("企业微信登录完成, userid: {}, sessionKey存在: {}, expiresIn: {}",
                    result.getUserId(),
                    result.getSessionKey() != null && !result.getSessionKey().isEmpty(),
                    result.getExpiresIn());

            return ApiResponse.success("登录成功", result);

        } catch (Exception e) {
            log.error("企业微信登录失败", e);
            return ApiResponse.error("登录失败: " + e.getMessage());
        }
    }

    /**
     * 检查session有效性
     */
    @PostMapping("/checkSession")
    public ApiResponse<Boolean> checkSession(@RequestBody CheckSessionRequest request) {
        try {
            boolean isValid = qyWechatService.validateSession(request.getSessionKey());
            log.info("session验证结果: {}", isValid);
            return ApiResponse.success(isValid);
        } catch (Exception e) {
            log.error("session验证失败", e);
            return ApiResponse.error("检查session失败: " + e.getMessage());
        }
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ApiResponse<String> healthCheck() {
        return ApiResponse.success("企业微信服务运行正常");
    }

    // 请求参数类
    public static class LoginRequest {
        private String code;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
    }

    public static class CheckSessionRequest {
        private String sessionKey;

        public String getSessionKey() { return sessionKey; }
        public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }
    }
}