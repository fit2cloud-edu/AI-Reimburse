package com.fit2cloud.fapiao.service;

import com.fit2cloud.fapiao.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
@Slf4j
public class WeChatWorkFileService {

    @Autowired
    private QyWechatService qyWechatService;

    @Autowired
    private RestTemplate restTemplate;

    private static final String DOWNLOAD_URL = "https://qyapi.weixin.qq.com/cgi-bin/wedrive/file_download";

    /**
     * 使用ticket下载企业微盘文件
     * @param ticket 文件下载凭证
     * @return 文件字节数组
     */
    public byte[] downloadFileFromWeDrive(String ticket) {
        try {
            log.info("开始下载微盘文件, ticket: {}", ticket);

            String accessToken = qyWechatService.getAccessTokenForApproval();

            // 构建请求体
            String requestBody = String.format("{\"selected_ticket\":\"%s\"}", ticket);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

            // 调用企业微信下载接口
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    DOWNLOAD_URL + "?access_token=" + accessToken,
                    HttpMethod.POST,
                    requestEntity,
                    byte[].class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                byte[] fileContent = response.getBody();
                log.info("微盘文件下载成功, 大小: {} bytes", fileContent.length);
                return fileContent;
            }

            throw new BusinessException("微盘文件下载失败, HTTP状态: " + response.getStatusCode());

        } catch (Exception e) {
            log.error("下载微盘文件异常", e);
            throw new BusinessException("下载微盘文件失败: " + e.getMessage());
        }
    }
}