package com.fit2cloud.fapiao.controller;

import com.fit2cloud.fapiao.dto.response.QyLoginResult;
import com.fit2cloud.fapiao.service.QyWechatWebService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
public class RootLoginController {

    private final QyWechatWebService qyWechatWebService;

    // 新增配置项注入
    @Value("${qywechat.app-id}")
    private String expectedAppId;

    @Value("${frontend.base-url}")
    private String frontendBaseUrl;

    @Value("${frontend.error-page}")
    private String frontendErrorPage;

    public RootLoginController(QyWechatWebService qyWechatWebService) {
        this.qyWechatWebService = qyWechatWebService;
    }

    /**
     * 处理企业微信回调登录 - 根路径接口
     * 路径：GET /login
     * 用于处理企业微信扫码后的回调
     */
    @GetMapping("/wechat-login")
    public ResponseEntity<String> handleWechatCallback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "appid", required = false) String appid) throws UnsupportedEncodingException {

        log.info("收到企业微信回调登录请求, code长度: {}, state: {}, appid: {}",
                code != null ? code.length() : 0, state, appid);

        try {
            // 验证企业微信应用ID
            if (appid != null && !appid.equals(expectedAppId)) {
                log.warn("企业微信应用ID不匹配: expected={}, actual={}", expectedAppId, appid);
            }

            // 调用企业微信Web端登录服务（使用网页授权接口）
            QyLoginResult result = qyWechatWebService.getWebUserInfoByCode(code);

            // 构建登录成功后的重定向URL
            String redirectUrl = buildRedirectUrl(result);
            log.info("企业微信登录成功, userid: {}, 重定向到: {}", result.getUserId(), redirectUrl);

            // 返回302重定向响应
            return ResponseEntity.status(302)
                    .header("Location", redirectUrl)
                    .build();

        } catch (Exception e) {
            log.error("企业微信回调登录处理失败", e);

            // 重定向到错误页面
            String errorUrl = frontendErrorPage +
                    "?message=" + URLEncoder.encode("登录失败：" + e.getMessage(), StandardCharsets.UTF_8.toString());

            return ResponseEntity.status(302)
                    .header("Location", errorUrl)
                    .build();
        }
    }

    /**
     * 构建重定向URL
     */
    private String buildRedirectUrl(QyLoginResult result) {
        StringBuilder urlBuilder = new StringBuilder();

        try {
            // 构建重定向到前端登录页面的URL，携带用户信息参数
            urlBuilder.append(frontendBaseUrl).append("/login");

            urlBuilder.append("?userid=").append(URLEncoder.encode(result.getUserId(), StandardCharsets.UTF_8.toString()));

            // 对中文用户名进行URL编码
            String userName = result.getUserName() != null ? result.getUserName() : "";
            urlBuilder.append("&name=").append(URLEncoder.encode(userName, StandardCharsets.UTF_8.toString()));

            // 添加部门信息
            if (result.getDepartmentId() != null) {
                urlBuilder.append("&departmentId=").append(URLEncoder.encode(result.getDepartmentId().toString(), StandardCharsets.UTF_8.toString()));
            }

            if (result.getDepartmentName() != null) {
                urlBuilder.append("&departmentName=").append(URLEncoder.encode(result.getDepartmentName(), StandardCharsets.UTF_8.toString()));
            }

            // 添加区域信息
            if (result.getRegion() != null) {
                urlBuilder.append("&region=").append(URLEncoder.encode(result.getRegion(), StandardCharsets.UTF_8.toString()));
            }

            // 添加session key（如果前端需要）
            if (result.getSessionKey() != null && !result.getSessionKey().isEmpty()) {
                urlBuilder.append("&sessionKey=").append(URLEncoder.encode(result.getSessionKey(), StandardCharsets.UTF_8.toString()));
            }

            // 添加登录成功标志（这些是英文，但为了统一也进行编码）
            urlBuilder.append("&loginSource=").append(URLEncoder.encode("qywechat", StandardCharsets.UTF_8.toString()));
            urlBuilder.append("&loginStatus=").append(URLEncoder.encode("success", StandardCharsets.UTF_8.toString()));

            String redirectUrl = urlBuilder.toString();
            log.info("构建重定向URL到前端登录页面: {}", redirectUrl);
            return redirectUrl;

        } catch (Exception e) {
            log.error("构建重定向URL失败", e);
            // 如果编码失败，返回一个基本的错误重定向URL
            return frontendBaseUrl + "/login?loginStatus=error&message=URL编码失败";
        }
    }

    /**
     * 根路径健康检查接口
     * 路径：GET /health
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("根路径服务运行正常");
    }
}