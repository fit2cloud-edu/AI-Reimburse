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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 企业微信Web端登录服务
 * 专门处理Web端的企业微信登录逻辑，复用现有的access_token获取逻辑
 */
@Service
@Slf4j
public class QyWechatWebService {

    @Value("${qywechat.corpid}")
    private String corpid;

    @Value("${qywechat.agentSecret}")
    private String agentSecret;

    private final RestTemplate restTemplate;
    private final QyWechatService qyWechatService;
    private final UserDepartmentRelationService userDepartmentRelationService; // 复用现有的access_token逻辑

    // Web端session缓存
    private final Map<String, WebSessionInfo> webSessionCache = new ConcurrentHashMap<>();

    // Web端session信息
    private static class WebSessionInfo {
        String userId;
        String userName;
        long createTime;
        long expireTime;

        WebSessionInfo(String userId, String userName, long expireSeconds) {
            this.userId = userId;
            this.userName = userName;
            this.createTime = System.currentTimeMillis();
            this.expireTime = this.createTime + expireSeconds * 1000;
        }

        boolean isValid() {
            return System.currentTimeMillis() < expireTime;
        }
    }

    public QyWechatWebService(RestTemplate restTemplate, QyWechatService qyWechatService,
                              UserDepartmentRelationService userDepartmentRelationService) {
        this.restTemplate = restTemplate;
        this.qyWechatService = qyWechatService;
        this.userDepartmentRelationService = userDepartmentRelationService; // 新增
    }

    /**
     * Web端企业微信登录 - 使用正确的网页授权接口
     */
    public QyLoginResult getWebUserInfoByCode(String code) {
        try {
            log.info("开始Web端企业微信登录, code: {}, corpid: {}", code, corpid);

            if (code == null || code.isEmpty()) {
                throw new BusinessException("登录code不能为空");
            }

            // 1. 复用现有的access_token获取逻辑（安全可靠）
            String accessToken = qyWechatService.getAccessTokenForApproval();

            // 2. 调用企业微信网页授权接口
            Map<String, Object> userInfo = getWebUserInfo(accessToken, code);
            String userId = (String) userInfo.get("userid");

            // 3. 获取用户部门结构信息
            Map<String, Object> departmentStructure = userDepartmentRelationService.getDepartmentStructureForUser(userId);

            // 4. 生成Web端专用的sessionKey
            String sessionKey = generateWebSessionKey();

            // 5. 缓存session信息
            cacheWebSession(sessionKey, userInfo);

            // 6. 构建返回结果
            QyLoginResult result = new QyLoginResult();
            result.setUserId(userId);
            result.setUserName((String) userInfo.get("name"));
            result.setSessionKey(sessionKey);
            result.setExpiresIn(7200);

            // 设置部门结构信息
            if (departmentStructure != null && !departmentStructure.isEmpty()) {
                result.setDepartmentStructure(departmentStructure);
                result.setDepartmentId((String) departmentStructure.get("departmentId"));
                result.setDepartmentName((String) departmentStructure.get("departmentName"));
                result.setDepartmentFullPath((String) departmentStructure.get("fullPath"));
                result.setDepartmentHierarchy((List<String>) departmentStructure.get("departmentHierarchy"));
                result.setRegion((String) departmentStructure.get("region"));
                result.setRegionDepartmentId((String) departmentStructure.get("regionDepartmentId"));

                log.info("用户 {} 的部门结构已添加到登录响应: ID={}, 名称={}, 路径={}, 区域={}",
                        userId, departmentStructure.get("departmentId"),
                        departmentStructure.get("departmentName"),
                        departmentStructure.get("fullPath"),
                        departmentStructure.get("region"));
            } else {
                log.warn("用户 {} 未获取到部门结构信息，使用默认值", userId);
                // 设置默认值
                result.setRegion("默认区域");
            }

            log.info("Web端企业微信登录成功, userid: {}, name: {}, sessionKey: {}",
                    result.getUserId(), result.getUserName(), sessionKey);

            return result;

        } catch (Exception e) {
            log.error("Web端企业微信登录失败", e);
            throw new BusinessException("Web端登录失败: " + e.getMessage());
        }
    }

    /**
     * 调用企业微信网页授权接口获取用户信息
     */
    private Map<String, Object> getWebUserInfo(String accessToken, String code) {
        try {
            // 使用正确的企业微信网页授权API
            String url = "https://qyapi.weixin.qq.com/cgi-bin/user/getuserinfo?access_token={accessToken}&code={code}";

            Map<String, String> params = new HashMap<>();
            params.put("accessToken", accessToken);
            params.put("code", code);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class, params);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer errcode = (Integer) responseBody.get("errcode");

                if (errcode != null && errcode == 0) {
                    String userId = (String) responseBody.get("UserId");
                    log.info("企业微信网页授权成功, userid: {}", userId);

                    // 获取用户详细信息
                    return getUserDetail(accessToken, userId);
                } else {
                    String errmsg = (String) responseBody.get("errmsg");
                    log.warn("企业微信网页授权失败, errcode: {}, errmsg: {}", errcode, errmsg);
                    throw new BusinessException("网页授权失败: " + errmsg);
                }
            } else {
                throw new BusinessException("企业微信API请求失败");
            }
        } catch (Exception e) {
            log.error("企业微信网页授权异常", e);
            throw new BusinessException("网页授权异常: " + e.getMessage());
        }
    }

    /**
     * 获取用户详细信息（复用现有逻辑）
     */
    private Map<String, Object> getUserDetail(String accessToken, String userId) {
        try {
            String url = "https://qyapi.weixin.qq.com/cgi-bin/user/get?access_token={accessToken}&userid={userId}";

            Map<String, String> params = new HashMap<>();
            params.put("accessToken", accessToken);
            params.put("userId", userId);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class, params);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer errcode = (Integer) responseBody.get("errcode");

                if (errcode != null && errcode == 0) {
                    Map<String, Object> userDetail = new HashMap<>();
                    userDetail.put("userid", userId);
                    userDetail.put("name", responseBody.get("name"));
                    userDetail.put("avatar", responseBody.get("avatar"));
                    userDetail.put("department", responseBody.get("department"));
                    return userDetail;
                }
            }

            // 如果获取详细信息失败，返回基本信息
            Map<String, Object> basicInfo = new HashMap<>();
            basicInfo.put("userid", userId);
            basicInfo.put("name", userId);
            return basicInfo;

        } catch (Exception e) {
            log.warn("获取用户详细信息失败: {}", e.getMessage());
            Map<String, Object> basicInfo = new HashMap<>();
            basicInfo.put("userid", userId);
            basicInfo.put("name", userId);
            return basicInfo;
        }
    }

    /**
     * 生成Web端专用的sessionKey
     */
    private String generateWebSessionKey() {
        return "web_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 缓存Web端session信息
     */
    private void cacheWebSession(String sessionKey, Map<String, Object> userInfo) {
        String userId = (String) userInfo.get("userid");
        String userName = (String) userInfo.get("name");

        WebSessionInfo sessionInfo = new WebSessionInfo(userId, userName, 7200);
        webSessionCache.put(sessionKey, sessionInfo);

        log.debug("缓存Web端session, sessionKey: {}, userid: {}", sessionKey, userId);
    }

    /**
     * 清理过期的Web端session
     */
    public void cleanupExpiredWebSessions() {
        int expiredCount = 0;
        for (String sessionKey : webSessionCache.keySet()) {
            WebSessionInfo sessionInfo = webSessionCache.get(sessionKey);
            if (sessionInfo != null && !sessionInfo.isValid()) {
                webSessionCache.remove(sessionKey);
                expiredCount++;
            }
        }

        if (expiredCount > 0) {
            log.info("清理了 {} 个过期的Web端session", expiredCount);
        }
    }

    /**
     * 验证Web端session有效性
     */
    public boolean validateWebSession(String sessionKey) {
        if (sessionKey == null || sessionKey.isEmpty()) {
            return false;
        }

        WebSessionInfo sessionInfo = webSessionCache.get(sessionKey);
        if (sessionInfo == null) {
            log.warn("sessionKey不存在: {}", sessionKey);
            return false;
        }

        boolean isValid = sessionInfo.isValid();
        if (!isValid) {
            webSessionCache.remove(sessionKey);
            log.info("sessionKey已过期: {}", sessionKey);
        }

        return isValid;
    }

    /**
     * 生成JS-SDK签名
     * @param url 当前网页的URL
     * @return 签名信息
     */
    public Map<String, String> generateJsSdkSignature(String url) {
        try {
            log.info("开始生成JS-SDK签名, url: {}", url);

            // 1. 获取jsapi_ticket
            String jsapiTicket = getJsapiTicket();

            // 2. 生成随机字符串
            String nonceStr = generateNonceStr();

            // 3. 生成时间戳
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);

            // 4. 生成签名
            String signature = generateSignature(jsapiTicket, nonceStr, timestamp, url);

            // 5. 构建返回结果
            Map<String, String> result = new HashMap<>();
            result.put("timestamp", timestamp);
            result.put("nonceStr", nonceStr);
            result.put("signature", signature);
            result.put("appId", corpid); // 企业微信的corpid就是appId

            log.info("JS-SDK签名生成成功, timestamp: {}, nonceStr: {}, signature: {}",
                    timestamp, nonceStr, signature);

            return result;

        } catch (Exception e) {
            log.error("JS-SDK签名生成失败", e);
            throw new BusinessException("JS-SDK签名生成失败: " + e.getMessage());
        }
    }

    /**
     * 获取jsapi_ticket
     */
    private String getJsapiTicket() {
        try {
            // 复用现有的access_token获取逻辑
            String accessToken = qyWechatService.getAccessTokenForApproval();

            String url = "https://qyapi.weixin.qq.com/cgi-bin/get_jsapi_ticket?access_token={accessToken}";

            Map<String, String> params = new HashMap<>();
            params.put("accessToken", accessToken);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class, params);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer errcode = (Integer) responseBody.get("errcode");

                if (errcode != null && errcode == 0) {
                    String ticket = (String) responseBody.get("ticket");
                    log.info("获取jsapi_ticket成功");
                    return ticket;
                } else {
                    String errmsg = (String) responseBody.get("errmsg");
                    log.error("获取jsapi_ticket失败, errcode: {}, errmsg: {}", errcode, errmsg);
                    throw new BusinessException("获取jsapi_ticket失败: " + errmsg);
                }
            } else {
                throw new BusinessException("jsapi_ticket API请求失败");
            }
        } catch (Exception e) {
            log.error("获取jsapi_ticket异常", e);
            throw new BusinessException("获取jsapi_ticket异常: " + e.getMessage());
        }
    }

    /**
     * 生成随机字符串
     */
    private String generateNonceStr() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 生成签名
     */
    private String generateSignature(String jsapiTicket, String nonceStr, String timestamp, String url) {
        try {
            // 按照企业微信官方文档要求拼接字符串
            String string1 = "jsapi_ticket=" + jsapiTicket +
                    "&noncestr=" + nonceStr +
                    "&timestamp=" + timestamp +
                    "&url=" + url;

            log.debug("签名原始字符串: {}", string1);

            // 使用SHA1加密
            java.security.MessageDigest crypt = java.security.MessageDigest.getInstance("SHA-1");
            crypt.reset();
            crypt.update(string1.getBytes("UTF-8"));
            String signature = bytesToHex(crypt.digest());

            log.debug("生成签名: {}", signature);
            return signature;

        } catch (Exception e) {
            log.error("生成签名异常", e);
            throw new BusinessException("生成签名异常: " + e.getMessage());
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}