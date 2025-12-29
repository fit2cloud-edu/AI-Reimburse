package com.fit2cloud.fapiao.service;

import com.fit2cloud.fapiao.dto.response.QyLoginResult;
import com.fit2cloud.fapiao.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class QyWechatService {

    @Value("${qywechat.corpid}")
    private String corpid;

    @Value("${qywechat.agentSecret}")
    private String agentSecret;

    @Value("${qywechat.approval-secret:}")
    private String approvalSecret;

    @Value("${qywechat.address-book-secret:}")
    private String addressBookSecret;

    private final RestTemplate restTemplate;
    private final AccessTokenService accessTokenService;
    // 使用不同的token缓存，按secret类型区分
    private final Map<String, TokenInfo> tokenCache = new ConcurrentHashMap<>();

    private final UserDepartmentRelationService userDepartmentRelationService;


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

    public QyWechatService(RestTemplate restTemplate,
                           AccessTokenService accessTokenService,
                           UserDepartmentRelationService userDepartmentRelationService) {
        this.restTemplate = restTemplate;
        this.accessTokenService = accessTokenService;
        this.userDepartmentRelationService = userDepartmentRelationService;
    }

    /**
     * 通过code获取用户信息 - 实现企业微信登录流程
     */
    public QyLoginResult getUserInfoByCode(String code) {
        try {
            log.info("开始企业微信登录, code: {}, corpid: {}", code, corpid);

            if (code == null || code.isEmpty()) {
                throw new BusinessException("登录code不能为空");
            }

            // 1. 获取access_token（使用agentSecret）
            String accessToken = accessTokenService.getAccessToken("agent", agentSecret);

            // 2. 调用code2Session接口
            Map<String, Object> sessionInfo = code2Session(accessToken, code);

            // 3. 构建返回结果
            QyLoginResult result = new QyLoginResult();
            result.setUserId((String) sessionInfo.get("userid"));
            result.setSessionKey((String) sessionInfo.get("session_key"));
            result.setUserTicket((String) sessionInfo.get("user_ticket")); // 可能为空

            // 获取用户部门结构信息
            Map<String, Object> departmentStructure = userDepartmentRelationService.getDepartmentStructureForUser(result.getUserId());

            // 设置部门结构信息到返回结果
            if (departmentStructure != null && !departmentStructure.isEmpty()) {
                result.setDepartmentStructure(departmentStructure);
                result.setDepartmentId((String) departmentStructure.get("departmentId"));
                result.setDepartmentName((String) departmentStructure.get("departmentName"));
                result.setDepartmentFullPath((String) departmentStructure.get("fullPath"));
                result.setDepartmentHierarchy((List<String>) departmentStructure.get("departmentHierarchy"));
                result.setRegion((String) departmentStructure.get("region"));
                result.setRegionDepartmentId((String) departmentStructure.get("regionDepartmentId"));
            }

            // 返回过期时间（从企业微信响应中获取）
            Object expiresIn = sessionInfo.get("expires_in");
            if (expiresIn != null) {
                result.setExpiresIn(Integer.valueOf(expiresIn.toString()));
            } else {
                result.setExpiresIn(7200);
            }

            log.info("企业微信登录成功, userid: {}, session_key: {}",
                    result.getUserId(),
                    result.getSessionKey() != null ? "已获取" : "为空");

            return result;

        } catch (Exception e) {
            log.error("获取企业微信用户信息失败", e);
            throw new BusinessException("获取用户信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定类型的access_token
     */
    private String getAccessToken(String tokenType, String secret) {
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
     * 调用code2Session接口
     */
    private Map<String, Object> code2Session(String accessToken, String code) {
        try {
            String url = "https://qyapi.weixin.qq.com/cgi-bin/miniprogram/jscode2session?access_token={accessToken}&js_code={code}&grant_type=authorization_code";

            Map<String, String> params = new HashMap<>();
            params.put("accessToken", accessToken);
            params.put("code", code);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class, params);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer errcode = (Integer) responseBody.get("errcode");

                if (errcode != null && errcode == 0) {
                    log.info("code2Session成功, userid: {}", responseBody.get("userid"));
                    return responseBody;
                } else {
                    String errmsg = (String) responseBody.get("errmsg");
                    log.warn("code2Session失败, errcode: {}, errmsg: {}", errcode, errmsg);
                    throw new BusinessException("登录验证失败: " + errmsg);
                }
            } else {
                throw new BusinessException("code2Session请求失败");
            }
        } catch (Exception e) {
            log.error("code2Session异常", e);
            throw new BusinessException("登录验证异常: " + e.getMessage());
        }
    }

    /**
     * 验证session是否有效
     */
    public boolean validateSession(String sessionKey) {
        // 这里可以添加更复杂的session验证逻辑
        // 比如检查session是否过期，或者调用企业微信接口验证
        return sessionKey != null && !sessionKey.isEmpty();
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