package com.fit2cloud.fapiao.service;

import com.fit2cloud.fapiao.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class AccessTokenService {

    @Value("${qywechat.corpid}")
    private String corpid;

    @Value("${qywechat.agentSecret}")
    private String agentSecret;

    @Value("${qywechat.approval-secret:}")
    private String approvalSecret;

    @Value("${qywechat.address-book-secret:}")
    private String addressBookSecret;

    private final RestTemplate restTemplate;

    // 使用不同的token缓存，按secret类型区分
    private final Map<String, TokenInfo> tokenCache = new ConcurrentHashMap<>();

    // Token信息类
    private static class TokenInfo {
        String token;
        long expireTime;

        TokenInfo(String token, long expireTime) {
            this.token = token;
            this.expireTime = expireTime;
        }

        boolean isValid() {
            return token != null && System.currentTimeMillis() < expireTime - 5 * 60 * 1000; // 提前5分钟刷新
        }
    }

    public AccessTokenService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 获取指定类型的access_token
     */
    public String getAccessToken(String tokenType, String secret) {
        if (secret == null || secret.trim().isEmpty()) {
            throw new BusinessException("未配置" + tokenType + "对应的secret");
        }

        // 检查缓存中是否有有效的token
        TokenInfo tokenInfo = tokenCache.get(tokenType);
        if (tokenInfo != null && tokenInfo.isValid()) {
            return tokenInfo.token;
        }

        try {
            String url = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid={corpid}&corpsecret={secret}";

            Map<String, String> params = new HashMap<>();
            params.put("corpid", corpid);
            params.put("secret", secret);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class, params);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer errcode = (Integer) responseBody.get("errcode");

                if (errcode != null && errcode == 0) {
                    String token = (String) responseBody.get("access_token");
                    Integer expiresIn = (Integer) responseBody.get("expires_in");
                    long expireTime = System.currentTimeMillis() + expiresIn * 1000L;

                    // 更新缓存
                    tokenCache.put(tokenType, new TokenInfo(token, expireTime));

                    log.info("获取 {} access_token成功, 有效期: {}秒", tokenType, expiresIn);
                    return token;
                } else {
                    String errmsg = (String) responseBody.get("errmsg");
                    log.error("获取 {} access_token失败: {}", tokenType, errmsg);
                    throw new BusinessException("获取access_token失败: " + errmsg);
                }
            } else {
                throw new BusinessException("获取access_token请求失败");
            }
        } catch (Exception e) {
            log.error("获取 {} access_token异常", tokenType, e);
            throw new BusinessException("获取access_token异常: " + e.getMessage());
        }
    }

    /**
     * 专门为审批流程获取access_token（使用审批secret）
     */
    public String getAccessTokenForApproval() {
        // 如果没有单独配置审批secret，则使用agentSecret
        String secret = approvalSecret != null && !approvalSecret.trim().isEmpty()
                ? approvalSecret : agentSecret;
        return getAccessToken("approval", secret);
    }

    /**
     * 专门为通讯录同步获取access_token（使用通讯录secret）
     */
    public String getAccessTokenForAddressBook() {
        if (addressBookSecret == null || addressBookSecret.trim().isEmpty()) {
            log.error("未配置通讯录同步secret，请检查application.yml配置");
            throw new BusinessException("未配置通讯录同步secret");
        }
        return getAccessToken("addressBook", addressBookSecret);
    }

    /**
     * 获取部门列表等通用接口使用的access_token（使用agentSecret）
     */
    public String getAccessTokenForDepartment() {
        return getAccessToken("agent", agentSecret);
    }

    /**
     * 获取用户详情等接口使用的access_token（使用agentSecret）
     */
    public String getAccessTokenForUserInfo() {
        return getAccessToken("userInfo", agentSecret);
    }

    /**
     * 清除指定类型的token缓存
     */
    public void clearTokenCache(String tokenType) {
        tokenCache.remove(tokenType);
        log.info("已清除 {} token缓存", tokenType);
    }

    /**
     * 清除所有token缓存
     */
    public void clearAllTokenCache() {
        tokenCache.clear();
        log.info("已清除所有token缓存");
    }

    /**
     * 获取token缓存状态
     */
    public Map<String, Object> getTokenCacheStatus() {
        Map<String, Object> status = new HashMap<>();
        for (Map.Entry<String, TokenInfo> entry : tokenCache.entrySet()) {
            Map<String, Object> tokenStatus = new HashMap<>();
            tokenStatus.put("hasToken", entry.getValue() != null);
            if (entry.getValue() != null) {
                long age = System.currentTimeMillis() - (entry.getValue().expireTime - 7200 * 1000L);
                tokenStatus.put("tokenAge", age / 1000 + "秒");
                tokenStatus.put("expiresIn", (entry.getValue().expireTime - System.currentTimeMillis()) / 1000 + "秒");
                tokenStatus.put("isValid", entry.getValue().isValid());
            }
            status.put(entry.getKey(), tokenStatus);
        }
        return status;
    }
}
