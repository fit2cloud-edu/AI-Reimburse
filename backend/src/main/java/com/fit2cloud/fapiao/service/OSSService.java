package com.fit2cloud.fapiao.service;

import com.fit2cloud.fapiao.dto.response.OSSUploadResponse;
import com.fit2cloud.fapiao.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class OSSService {

    @Value("${oss.upload-url}")
    private String ossUploadUrl;

    @Value("${maxkb.api-key}")
    private String apiKey;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 上传文件到OSS获取file_id
     */
    public OSSUploadResponse uploadFileToOSS(MultipartFile file) {
        try {
            log.info("开始上传文件到OSS, 文件名: {}, 大小: {}",
                    file.getOriginalFilename(), file.getSize());

            // 文件有效性检查
            if (file.isEmpty()) {
                // 检查是否是文件大小为0的情况
                if (file.getSize() == 0) {
                    throw new BusinessException("上传文件为空，文件大小为0字节");
                }
                throw new BusinessException("上传文件为空");
            }

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", "Bearer " + apiKey);

            // 构建 multipart 请求，使用 ByteArrayResource 避免临时文件问题
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            // 将 MultipartFile 转换为 ByteArrayResource
            byte[] fileBytes = file.getBytes();
            if (fileBytes.length == 0) {
                throw new BusinessException("文件内容为空，无法上传");
            }

            ByteArrayResource resource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            body.add("file", resource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            // 发送OSS上传请求
            ResponseEntity<OSSUploadResponse> response = restTemplate.exchange(
                    ossUploadUrl,
                    HttpMethod.POST,
                    requestEntity,
                    OSSUploadResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                OSSUploadResponse ossResponse = response.getBody();
                if ("200".equals(ossResponse.getCode())) {
                    log.info("OSS文件上传成功, file_id: {}",
                            ossResponse.getData().getFile_id());
                    return ossResponse;
                } else {
                    throw new BusinessException("OSS上传失败: " + ossResponse.getMessage());
                }
            } else {
                throw new BusinessException("OSS上传请求失败: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("OSS文件上传异常", e);
            throw new BusinessException("文件上传服务异常: " + e.getMessage());
        }
    }
}
