package com.fit2cloud.fapiao.service;

import com.fit2cloud.fapiao.dto.request.ChatMessageRequest;
import com.fit2cloud.fapiao.dto.response.ChatMessageResponse;
import com.fit2cloud.fapiao.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

@Service
@Slf4j
public class MaxKBService {

    @Value("${maxkb.base-url}")
    private String baseUrl;

    @Value("${maxkb.api-key}")
    private String apiKey;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 获取基础域名URL
     */
    private String getBaseDomain() {
        try {
            URI uri = new URI(baseUrl);
            return uri.getScheme() + "://" + uri.getHost();
        } catch (URISyntaxException e) {
            log.error("解析基础URL失败", e);
            throw new BusinessException("URL配置错误");
        }
    }

    /**
     * 获取会话ID
     */
    public String getChatId() {
        try {
            // 从完整baseURL中提取基础域名，然后拼接正确的API路径
            String baseDomain = getBaseDomain();
            String openUrl = baseDomain + "/chat/api/open";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 使用Bearer Token方式认证
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<String> requestEntity = new HttpEntity<>("{}", headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    openUrl,
                    HttpMethod.GET,
                    requestEntity,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                if ("200".equals(responseBody.get("code").toString())) {
                    // data字段直接就是chat_id字符串
                    Object data = responseBody.get("data");
                    if (data instanceof String) {
                        return (String) data;
                    } else {
                        throw new BusinessException("获取会话ID失败: data字段格式不正确");
                    }
                } else {
                    throw new BusinessException("获取会话ID失败: " + responseBody.get("message"));
                }
            }

            throw new BusinessException("获取会话ID失败，HTTP状态码: " + response.getStatusCode());
        } catch (Exception e) {
            log.error("获取会话ID异常", e);
            throw new BusinessException("获取会话ID异常: " + e.getMessage());
        }
    }

    /**
     * 调用智能体对话接口
     */
    public ChatMessageResponse sendChatMessage(String chatId, ChatMessageRequest request) {
        try {
            // 正确的URL构建方式
            String baseDomain = getBaseDomain();
            String chatUrl = baseDomain + "/chat/api/chat_message/" + chatId;

            // 在调用智能体对话接口前的日志应该更准确地反映文件类型
            log.info("调用智能体对话接口, 会话ID: {}, 消息: {}, 图片数量: {}, 文档数量: {}",
                    chatId, request.getMessage(), request.getImage_list().size(), request.getDocument_list().size());

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 使用Bearer Token方式认证
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<ChatMessageRequest> requestEntity =
                    new HttpEntity<>(request, headers);

            // 发送对话请求
            ResponseEntity<ChatMessageResponse> response = restTemplate.exchange(
                    chatUrl,
                    HttpMethod.POST,
                    requestEntity,
                    ChatMessageResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ChatMessageResponse chatResponse = response.getBody();
                if ("200".equals(chatResponse.getCode())) {
                    log.info("智能体对话成功, 返回内容长度: {}",
                            chatResponse.getData().getContent().length());
                    return chatResponse;
                } else {
                    throw new BusinessException("智能体对话失败: " + chatResponse.getMessage());
                }
            } else {
                throw new BusinessException("智能体对话请求失败: " + response.getStatusCode());
            }

        } catch (HttpServerErrorException.InternalServerError e) {
            // 处理500错误，可能是业务逻辑错误
            String responseBody = e.getResponseBodyAsString();
            log.error("智能体对话接口500错误，响应内容: {}", responseBody);

            // 尝试解析响应体判断是否为业务错误
            if (responseBody.contains("not a valid UUID")) {
                throw new BusinessException("文件ID格式错误，请检查上传的文件");
            }

            throw new BusinessException("智能体服务内部错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("调用智能体对话接口异常", e);
            throw new BusinessException("智能体服务异常: " + e.getMessage());
        }
    }
}
