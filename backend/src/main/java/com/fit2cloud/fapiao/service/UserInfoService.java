// #file src/main/java/com/fit2cloud/fapiao/service/UserInfoService.java
package com.fit2cloud.fapiao.service;

import com.fit2cloud.fapiao.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Slf4j
public class UserInfoService {

    @Autowired
    private AccessTokenService accessTokenService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private UserDepartmentRelationService userDepartmentRelationService;

    // 用户信息缓存
    private Map<String, Map<String, Object>> userInfoCache = new HashMap<>();
    private long userInfoCacheTimestamp;
    private static final long USER_INFO_CACHE_DURATION = 60 * 60 * 1000; // 1小时缓存

    // 姓名到用户ID列表的缓存（支持重名）
    private Map<String, List<String>> nameToUserIdCache = new HashMap<>();

    // 新增：每个用户的缓存时间戳
    private Map<String, Long> userCacheTimestamps = new HashMap<>();

    // 添加一个标志，避免重复加载
    private volatile boolean isInitialized = false;
    private final Object initLock = new Object();

    // 在构造函数或初始化方法中加载所有用户信息
    @PostConstruct
    public void init() {
        synchronized (initLock) {
            if (isInitialized) {
                log.info("用户信息缓存已初始化，跳过重复初始化");
                return;
            }

            log.info("初始化用户信息缓存...");
            loadAllUserInfo();
            isInitialized = true;
        }
    }

    // 添加定时刷新任务
    @Scheduled(fixedDelay = 60 * 60 * 1000) // 每小时刷新一次
    public void scheduledRefresh() {
        log.info("定时刷新用户信息缓存...");
        refreshUserInfoCache(null); // 刷新所有用户
    }

    /**
     * 加载所有用户信息到缓存
     */
    public void loadAllUserInfo() {

        synchronized (initLock) {
            // 如果缓存已经有数据，且未过期，跳过加载
            if (!userInfoCache.isEmpty() && isOverallCacheValid()) {
                log.info("用户信息缓存有效，跳过加载");
                return;
            }

            try {
                // 清空旧缓存
                userInfoCache.clear();
                nameToUserIdCache.clear();
                userCacheTimestamps.clear();

                // 获取所有用户ID
                Map<String, List<String>> userRelations = userDepartmentRelationService.getUserDepartmentRelations();
                List<String> allUserIds = new ArrayList<>(userRelations.keySet());

                log.info("开始加载 {} 个用户的详细信息", allUserIds.size());

                int successCount = 0;
                int failureCount = 0;

                for (String userId : allUserIds) {
                    try {
                        // 获取用户详细信息
                        String accessToken = accessTokenService.getAccessTokenForUserInfo();
                        String url = "https://qyapi.weixin.qq.com/cgi-bin/user/get?access_token=" +
                                accessToken + "&userid=" + userId;

                        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

                        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                            Map<String, Object> responseBody = response.getBody();
                            Integer errcode = (Integer) responseBody.get("errcode");

                            if (errcode != null && errcode == 0) {
                                Map<String, Object> userInfo = processUserInfo(responseBody);

                                // 缓存用户信息和时间戳
                                userInfoCache.put(userId, userInfo);
                                userCacheTimestamps.put(userId, System.currentTimeMillis());
                                successCount++;

                                // 每加载10个用户打印一次进度
                                if (successCount % 10 == 0) {
                                    log.info("已加载 {} 个用户信息", successCount);
                                }
                            } else {
                                String errmsg = (String) responseBody.get("errmsg");
                                log.warn("加载用户 {} 信息失败, errcode: {}, errmsg: {}",
                                        userId, errcode, errmsg);
                                failureCount++;
                            }
                        } else {
                            log.warn("加载用户 {} 信息请求失败, 状态码: {}",
                                    userId, response.getStatusCode());
                            failureCount++;
                        }
                    } catch (Exception e) {
                        log.warn("加载用户 {} 信息异常: {}", userId, e.getMessage());
                        failureCount++;
                    }

                    // 避免请求太快，添加短暂延迟
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                // 更新整体缓存时间戳
                userInfoCacheTimestamp = System.currentTimeMillis();

                log.info("用户信息缓存加载完成，成功: {} 个，失败: {} 个，姓名映射: {} 条",
                        successCount, failureCount, nameToUserIdCache.size());

                // 记录一些统计信息
                logNameCacheStatistics();

            } catch (Exception e) {
                log.error("加载用户信息缓存异常", e);
            }
        }
    }

    /**
     * 记录姓名缓存的统计信息
     */
    private void logNameCacheStatistics() {
        if (nameToUserIdCache.isEmpty()) {
            log.warn("姓名缓存为空，可能是用户信息加载失败");
            return;
        }

        // 统计重名情况
        int duplicateNameCount = 0;
        for (Map.Entry<String, List<String>> entry : nameToUserIdCache.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicateNameCount++;
                log.info("发现重名: {} 对应 {} 个用户: {}",
                        entry.getKey(), entry.getValue().size(), entry.getValue());
            }
        }

        log.info("姓名缓存统计：总姓名数: {}，重名数: {}，总用户数: {}",
                nameToUserIdCache.size(), duplicateNameCount, userInfoCache.size());
    }

    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("userInfoCacheSize", userInfoCache.size());
        stats.put("nameMappingCacheSize", nameToUserIdCache.size());
        stats.put("userCacheTimestampsSize", userCacheTimestamps.size());

        // 计算缓存有效性统计
        int validCacheCount = 0;
        long oldestCacheTime = Long.MAX_VALUE;
        long newestCacheTime = 0;

        for (Long cacheTime : userCacheTimestamps.values()) {
            if (isCacheValid(cacheTime, USER_INFO_CACHE_DURATION)) {
                validCacheCount++;
            }

            if (cacheTime < oldestCacheTime) {
                oldestCacheTime = cacheTime;
            }

            if (cacheTime > newestCacheTime) {
                newestCacheTime = cacheTime;
            }
        }

        stats.put("validCacheCount", validCacheCount);
        stats.put("invalidCacheCount", userCacheTimestamps.size() - validCacheCount);
        stats.put("cacheValidityRate", userCacheTimestamps.size() > 0 ?
                (double) validCacheCount / userCacheTimestamps.size() : 0);

        // 计算重名统计
        int duplicateNameCount = 0;
        int totalMappings = 0;

        for (List<String> userIds : nameToUserIdCache.values()) {
            totalMappings += userIds.size();
            if (userIds.size() > 1) {
                duplicateNameCount++;
            }
        }

        stats.put("totalNameMappings", totalMappings);
        stats.put("duplicateNameCount", duplicateNameCount);
        stats.put("averageMappingsPerName", nameToUserIdCache.size() > 0 ?
                (double) totalMappings / nameToUserIdCache.size() : 0);

        // 缓存时间信息
        stats.put("userInfoCacheTimestamp", userInfoCacheTimestamp);
        stats.put("cacheAgeSeconds", userInfoCacheTimestamp > 0 ?
                (System.currentTimeMillis() - userInfoCacheTimestamp) / 1000 : 0);
        stats.put("isOverallCacheValid", isOverallCacheValid());

        // 最旧和最新缓存时间
        if (oldestCacheTime != Long.MAX_VALUE) {
            stats.put("oldestCacheAgeSeconds", (System.currentTimeMillis() - oldestCacheTime) / 1000);
        }

        if (newestCacheTime > 0) {
            stats.put("newestCacheAgeSeconds", (System.currentTimeMillis() - newestCacheTime) / 1000);
        }

        return stats;
    }

    /**
     * 清理过期缓存
     */
    public void cleanupExpiredCache() {
        log.info("开始清理过期用户缓存...");

        int expiredCount = 0;
        List<String> expiredUserIds = new ArrayList<>();

        for (Map.Entry<String, Long> entry : userCacheTimestamps.entrySet()) {
            String userId = entry.getKey();
            Long cacheTime = entry.getValue();

            if (cacheTime != null && !isCacheValid(cacheTime, USER_INFO_CACHE_DURATION * 2)) {
                // 超过2倍缓存时间的认为是过期（宽松策略）
                expiredUserIds.add(userId);
                expiredCount++;
            }
        }

        // 清理过期缓存
        for (String userId : expiredUserIds) {
            userInfoCache.remove(userId);
            userCacheTimestamps.remove(userId);
        }

        log.info("清理完成，移除了 {} 个过期用户缓存", expiredCount);
    }

    /**
     * 缓存姓名到用户ID的映射
     */
    private void cacheNameToUserId(String name, String userId) {
        if (name == null || name.isEmpty() || userId == null || userId.isEmpty()) {
            return;
        }

        nameToUserIdCache.computeIfAbsent(name, k -> new ArrayList<>()).add(userId);

        log.debug("缓存姓名映射: {} -> {}", name, userId);
    }

    /**
     * 获取成员详细信息
     */
    public Map<String, Object> getUserInfo(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new BusinessException("用户ID不能为空");
        }

        // 检查缓存
        if (userInfoCache.containsKey(userId)) {
            Map<String, Object> cachedInfo = userInfoCache.get(userId);

            // 检查该用户的缓存时间（需要记录每个用户的缓存时间）
            Long userCacheTime = userCacheTimestamps.get(userId);
            if (userCacheTime != null && isCacheValid(userCacheTime, USER_INFO_CACHE_DURATION)) {
                log.debug("使用缓存的用户信息: {}", userId);
                return new HashMap<>(cachedInfo);
            }
        }

        // 缓存无效，重新获取
        return loadAndCacheUserInfo(userId);

    }

    /**
     * 加载并缓存用户信息
     */
    private Map<String, Object> loadAndCacheUserInfo(String userId) {
        log.info("加载用户信息: {}", userId);

        try {
            String accessToken = accessTokenService.getAccessTokenForUserInfo();
            String url = "https://qyapi.weixin.qq.com/cgi-bin/user/get?access_token=" +
                    accessToken + "&userid=" + userId;

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer errcode = (Integer) responseBody.get("errcode");

                if (errcode != null && errcode == 0) {
                    Map<String, Object> userInfo = processUserInfo(responseBody);

                    // 缓存用户信息
                    userInfoCache.put(userId, userInfo);
                    userCacheTimestamps.put(userId, System.currentTimeMillis());

                    log.info("成功加载并缓存用户 {} 的信息", userId);
                    return new HashMap<>(userInfo);
                } else {
                    String errmsg = (String) responseBody.get("errmsg");
                    log.error("加载用户 {} 信息失败, errcode: {}, errmsg: {}",
                            userId, errcode, errmsg);

                    // 如果失败，尝试返回缓存中的旧数据（如果有）
                    Map<String, Object> cachedInfo = userInfoCache.get(userId);
                    if (cachedInfo != null) {
                        log.warn("使用旧的缓存数据返回用户 {} 信息", userId);
                        return new HashMap<>(cachedInfo);
                    }

                    throw new BusinessException("获取用户信息失败: " + errmsg);
                }
            } else {
                throw new BusinessException("获取用户信息请求失败");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("加载用户信息异常", e);
            throw new BusinessException("获取用户信息异常: " + e.getMessage());
        }
    }

    /**
     * 批量获取用户信息（优化性能）
     */
    public List<Map<String, Object>> batchGetUserInfo(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        List<String> needToLoadUserIds = new ArrayList<>();

        // 先检查缓存
        for (String userId : userIds) {
            if (isUserCacheValid(userId)) {
                Map<String, Object> cachedInfo = userInfoCache.get(userId);
                if (cachedInfo != null) {
                    result.add(new HashMap<>(cachedInfo));
                }
            } else {
                needToLoadUserIds.add(userId);
            }
        }

        // 加载需要更新的用户信息
        for (String userId : needToLoadUserIds) {
            try {
                Map<String, Object> userInfo = loadAndCacheUserInfo(userId);
                result.add(userInfo);
            } catch (Exception e) {
                log.warn("批量获取用户 {} 信息失败: {}", userId, e.getMessage());
                // 继续处理其他用户
            }
        }

        return result;
    }

    /**
     * 根据部门ID获取部门下所有成员
     */
    public List<Map<String, Object>> getUsersByDepartment(String departmentId) {
        try {
            String accessToken = accessTokenService.getAccessTokenForUserInfo();
            String url = "https://qyapi.weixin.qq.com/cgi-bin/user/list?access_token=" + accessToken +
                    "&department_id=" + departmentId + "&fetch_child=1";

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer errcode = (Integer) responseBody.get("errcode");

                if (errcode != null && errcode == 0) {
                    List<Map<String, Object>> userList = (List<Map<String, Object>>) responseBody.get("userlist");
                    List<Map<String, Object>> result = new ArrayList<>();

                    if (userList != null) {
                        for (Map<String, Object> user : userList) {
                            result.add(processUserInfo(user));
                        }
                    }

                    log.info("获取部门 {} 下成员成功，共 {} 人", departmentId, result.size());
                    return result;
                } else {
                    String errmsg = (String) responseBody.get("errmsg");
                    log.error("获取部门成员失败, errcode: {}, errmsg: {}", errcode, errmsg);
                    return Collections.emptyList();
                }
            }
        } catch (Exception e) {
            log.error("获取部门成员异常", e);
        }

        return Collections.emptyList();
    }

    /**
     * 处理用户信息，提取需要的字段
     */
    private Map<String, Object> processUserInfo(Map<String, Object> rawUserInfo) {
        Map<String, Object> processedInfo = new HashMap<>();

        String userId = (String) rawUserInfo.get("userid");
        String name = (String) rawUserInfo.get("name");

        // 基本信息
        processedInfo.put("userId", userId);
        processedInfo.put("name", name);
        processedInfo.put("departmentIds", rawUserInfo.get("department"));
        processedInfo.put("position", rawUserInfo.get("position"));
        processedInfo.put("mobile", rawUserInfo.get("mobile"));
        processedInfo.put("email", rawUserInfo.get("email"));
        processedInfo.put("avatar", rawUserInfo.get("avatar"));
        processedInfo.put("status", rawUserInfo.get("status"));
        processedInfo.put("mainDepartment", rawUserInfo.get("main_department"));

        // 缓存姓名到用户ID的映射
        if (name != null && !name.trim().isEmpty()) {
            cacheNameToUserId(name.trim(), userId);
        }

        // 部门信息
        Object deptObj = rawUserInfo.get("department");
        List<String> departmentIds = new ArrayList<>();

        if (deptObj instanceof List) {
            List<?> deptList = (List<?>) deptObj;
            for (Object item : deptList) {
                if (item != null) {
                    departmentIds.add(item.toString());
                }
            }
        } else if (deptObj != null) {
            departmentIds.add(deptObj.toString());
        }

        processedInfo.put("departmentIds", departmentIds);


        if (departmentIds != null && !departmentIds.isEmpty()) {
            List<String> departmentNames = new ArrayList<>();
            List<String> departmentFullPaths = new ArrayList<>();

            try {
                List<Map<String, Object>> allDepartments = departmentService.getDepartmentList();

                for (String deptIdStr : departmentIds) {
                    for (Map<String, Object> dept : allDepartments) {
                        Integer id = (Integer) dept.get("id");
                        if (id != null && deptIdStr.equals(id.toString())) {
                            departmentNames.add((String) dept.get("name"));
                            departmentFullPaths.add((String) dept.get("fullPath"));
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("获取部门名称失败", e);
            }

            processedInfo.put("departmentNames", departmentNames);
            processedInfo.put("departmentFullPaths", departmentFullPaths);
        }

        return processedInfo;
    }

    /**
     * 检查缓存是否有效（通用方法）
     */
    private boolean isCacheValid(long cacheTimestamp, long cacheDuration) {
        return System.currentTimeMillis() - cacheTimestamp < cacheDuration;
    }

    /**
     * 检查用户缓存是否有效
     */
    public boolean isUserCacheValid(String userId) {
        Long cacheTime = userCacheTimestamps.get(userId);
        return cacheTime != null && isCacheValid(cacheTime, USER_INFO_CACHE_DURATION);
    }

    /**
     * 检查整体缓存是否有效
     */
    public boolean isOverallCacheValid() {
        return !userInfoCache.isEmpty() &&
                isCacheValid(userInfoCacheTimestamp, USER_INFO_CACHE_DURATION);
    }

    /**
     * 强制刷新用户信息缓存
     */
    public void refreshUserInfoCache(String userId) {
        if (userId != null) {
            // 清除指定用户的缓存
            Map<String, Object> oldUserInfo = userInfoCache.remove(userId);

            // 从姓名缓存中移除该用户
            if (oldUserInfo != null) {
                String name = (String) oldUserInfo.get("name");
                if (name != null) {
                    List<String> userIds = nameToUserIdCache.get(name);
                    if (userIds != null) {
                        userIds.remove(userId);
                        if (userIds.isEmpty()) {
                            nameToUserIdCache.remove(name);
                        }
                    }
                }
            }

            log.info("清除用户 {} 缓存，重新加载...", userId);

            // 重新加载该用户信息
            loadUserInfo(userId);
        } else {
            // 清除所有缓存并重新加载
            log.info("清除所有用户缓存，重新加载...");
            loadAllUserInfo();
        }
    }

    /**
     * 加载单个用户信息
     */
    private void loadUserInfo(String userId) {
        try {
            String accessToken = accessTokenService.getAccessTokenForUserInfo();
            String url = "https://qyapi.weixin.qq.com/cgi-bin/user/get?access_token=" +
                    accessToken + "&userid=" + userId;

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer errcode = (Integer) responseBody.get("errcode");

                if (errcode != null && errcode == 0) {
                    Map<String, Object> userInfo = processUserInfo(responseBody);
                    userInfoCache.put(userId, userInfo);
                    log.info("重新加载用户 {} 信息成功", userId);
                }
            }
        } catch (Exception e) {
            log.error("加载用户 {} 信息失败", userId, e);
        }
    }

    /**
     * 搜索用户（根据姓名或拼音）
     */
    public List<Map<String, Object>> searchUsers(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }

        log.info("搜索用户，关键词: {}", keyword);

        // 尝试从缓存中搜索
        List<Map<String, Object>> cachedUsers = searchUsersFromCache(keyword);
        if (!cachedUsers.isEmpty()) {
            log.info("从缓存中找到 {} 个匹配的用户", cachedUsers.size());
            return cachedUsers;
        }

        // 如果没有缓存，获取所有用户（这里可以优化为只获取一次）
        try {
            // 使用统一的用户信息获取方法
            List<Map<String, Object>> allUsers = getAllUsersFromEnterpriseWechat();
            List<Map<String, Object>> matchedUsers = new ArrayList<>();

            // 搜索匹配的用户
            String lowerKeyword = keyword.toLowerCase();
            for (Map<String, Object> user : allUsers) {
                String name = (String) user.get("name");
                String userId = (String) user.get("userId");
                String position = (String) user.get("position");

                if (name != null && name.toLowerCase().contains(lowerKeyword) ||
                        userId != null && userId.toLowerCase().contains(lowerKeyword) ||
                        position != null && position.toLowerCase().contains(lowerKeyword)) {
                    matchedUsers.add(user);

                    // 缓存用户信息到DepartmentService
                    if (userId != null && name != null) {
                        departmentService.cacheUserInfo(userId, name);
                    }
                }
            }

            log.info("用户搜索完成，关键词: {}, 匹配结果: {} 条", keyword, matchedUsers.size());
            return matchedUsers;

        } catch (Exception e) {
            log.error("搜索用户异常", e);
            return Collections.emptyList();
        }
    }

    /**
     * 从缓存中搜索用户
     */
    private List<Map<String, Object>> searchUsersFromCache(String keyword) {
        List<Map<String, Object>> matchedUsers = new ArrayList<>();
        String lowerKeyword = keyword.toLowerCase();

        // 这里可以实现更复杂的缓存搜索逻辑
        // 当前简单实现返回空，后续可以优化

        return matchedUsers;
    }

    /**
     * 从企业微信获取所有用户信息（优化版，减少重复调用）
     */
    private List<Map<String, Object>> getAllUsersFromEnterpriseWechat() {
        List<Map<String, Object>> allUsers = new ArrayList<>();

        try {
            // 获取通讯录同步的access_token
            String accessToken = accessTokenService.getAccessTokenForAddressBook();
            String url = "https://qyapi.weixin.qq.com/cgi-bin/user/list?access_token=" + accessToken +
                    "&department_id=1&fetch_child=1"; // 从根部门开始获取所有用户

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer errcode = (Integer) responseBody.get("errcode");

                if (errcode != null && errcode == 0) {
                    List<Map<String, Object>> userList = (List<Map<String, Object>>) responseBody.get("userlist");

                    if (userList != null) {
                        for (Map<String, Object> user : userList) {
                            Map<String, Object> processedUser = processUserInfo(user);
                            allUsers.add(processedUser);

                            // 缓存用户信息
                            String userId = (String) processedUser.get("userId");
                            String userName = (String) processedUser.get("name");
                            if (userId != null && userName != null) {
                                departmentService.cacheUserInfo(userId, userName);
                            }
                        }
                    }

                    log.info("从企业微信获取到 {} 个用户", allUsers.size());
                }
            }
        } catch (Exception e) {
            log.error("获取企业微信用户列表失败，使用备用方法", e);
            // 备用方法：遍历部门获取用户
            allUsers = getAllUsersByDepartments();
        }

        return allUsers;
    }

    /**
     * 备用方法：通过遍历部门获取所有用户
     */
    private List<Map<String, Object>> getAllUsersByDepartments() {
        List<Map<String, Object>> allUsers = new ArrayList<>();
        Set<String> userIds = new HashSet<>(); // 用于去重

        try {
            List<Map<String, Object>> allDepartments = departmentService.getDepartmentList();

            // 遍历每个部门获取成员
            for (Map<String, Object> dept : allDepartments) {
                Integer deptId = (Integer) dept.get("id");
                if (deptId != null) {
                    List<Map<String, Object>> deptUsers = getUsersByDepartment(deptId.toString());

                    for (Map<String, Object> user : deptUsers) {
                        String userId = (String) user.get("userId");
                        if (!userIds.contains(userId)) {
                            userIds.add(userId);
                            allUsers.add(user);

                            // 缓存用户信息
                            String userName = (String) user.get("name");
                            if (userId != null && userName != null) {
                                departmentService.cacheUserInfo(userId, userName);
                            }
                        }
                    }
                }
            }

            log.info("通过遍历部门获取到 {} 个去重用户", allUsers.size());
        } catch (Exception e) {
            log.error("通过遍历部门获取用户失败", e);
        }

        return allUsers;
    }

    /**
     * 验证人名是否为企业成员
     */
    public Map<String, Object> verifyEnterpriseMember(String name) {
        Map<String, Object> result = new HashMap<>();
        result.put("name", name);
        result.put("isEnterpriseMember", false);
        result.put("cacheStatus", "VALID"); // 默认缓存有效

        if (name == null || name.trim().isEmpty()) {
            return result;
        }

        String trimmedName = name.trim();
        log.info("验证人名是否为企业成员: {}", trimmedName);

        try {
            // 检查整体缓存是否有效
            if (!isOverallCacheValid()) {
                log.warn("用户信息缓存已过期，尝试重新加载...");
                result.put("cacheStatus", "EXPIRED");

                // 如果缓存过期，尝试重新加载
                try {
                    loadAllUserInfo();
                } catch (Exception e) {
                    log.error("重新加载用户缓存失败", e);
                    result.put("cacheStatus", "LOAD_FAILED");
                }
            }

            // 方案1：先检查姓名缓存（最快速）
            List<String> userIds = nameToUserIdCache.get(trimmedName);

            if (userIds != null && !userIds.isEmpty()) {
                result.put("isEnterpriseMember", true);
                result.put("hasMultipleMatches", userIds.size() > 1);

                // 获取匹配的用户详细信息
                List<Map<String, Object>> matchedUsers = new ArrayList<>();
                for (String userId : userIds) {
                    // 检查用户缓存是否有效
                    boolean userCacheValid = isUserCacheValid(userId);
                    Map<String, Object> userInfo = getUserInfo(userId); // 会自动刷新过期缓存
                    if (userInfo != null) {
                        // 只返回必要的字段，避免返回敏感信息
                        Map<String, Object> safeUserInfo = new HashMap<>();
                        safeUserInfo.put("userId", userInfo.get("userId"));
                        safeUserInfo.put("name", userInfo.get("name"));
                        safeUserInfo.put("position", userInfo.get("position"));
                        safeUserInfo.put("departmentNames", userInfo.get("departmentNames"));
                        safeUserInfo.put("departmentFullPaths", userInfo.get("departmentFullPaths"));
                        safeUserInfo.put("cacheValid", userCacheValid);
                        matchedUsers.add(safeUserInfo);
                    }
                }

                result.put("matchedUsers", matchedUsers);
                result.put("matchCount", matchedUsers.size());

                // 记录日志
                log.info("通过缓存验证：人名 {} 是企业成员，匹配到 {} 个用户",
                        trimmedName, matchedUsers.size());

                return result;
            }

            // 方案2：如果没有在缓存中找到，尝试模糊搜索（支持部分匹配）
            log.info("姓名 {} 未在缓存中找到，开始模糊搜索", trimmedName);
            List<Map<String, Object>> matchedUsers = fuzzySearchUsersByName(trimmedName);

            if (!matchedUsers.isEmpty()) {
                result.put("isEnterpriseMember", true);
                result.put("hasMultipleMatches", matchedUsers.size() > 1);
                result.put("matchedUsers", matchedUsers);
                result.put("matchCount", matchedUsers.size());
                result.put("isFuzzyMatch", true); // 标记是模糊匹配

                log.info("通过模糊搜索验证：人名 {} 是企业成员，匹配到 {} 个用户",
                        trimmedName, matchedUsers.size());
            } else {
                log.info("人名 {} 不是企业成员", trimmedName);
                result.put("isEnterpriseMember", false);
            }

        } catch (Exception e) {
            log.error("验证企业成员异常", e);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 模糊搜索用户（支持部分姓名匹配）
     */
    private List<Map<String, Object>> fuzzySearchUsersByName(String name) {
        List<Map<String, Object>> matchedUsers = new ArrayList<>();

        if (name == null || name.trim().isEmpty()) {
            return matchedUsers;
        }

        String lowerName = name.toLowerCase();

        // 遍历姓名缓存
        for (Map.Entry<String, List<String>> entry : nameToUserIdCache.entrySet()) {
            String cachedName = entry.getKey();

            // 完全匹配或包含关系
            if (cachedName.toLowerCase().contains(lowerName) ||
                    lowerName.contains(cachedName.toLowerCase())) {

                // 获取对应的所有用户信息
                for (String userId : entry.getValue()) {
                    Map<String, Object> userInfo = userInfoCache.get(userId);
                    if (userInfo != null) {
                        Map<String, Object> safeUserInfo = new HashMap<>();
                        safeUserInfo.put("userId", userInfo.get("userId"));
                        safeUserInfo.put("name", userInfo.get("name"));
                        safeUserInfo.put("position", userInfo.get("position"));
                        safeUserInfo.put("departmentNames", userInfo.get("departmentNames"));
                        matchedUsers.add(safeUserInfo);
                    }
                }
            }
        }

        return matchedUsers;
    }

    /**
     * 在所有用户中搜索姓名
     */
    private List<Map<String, Object>> searchAllUsersByName(String name) {
        List<Map<String, Object>> matchedUsers = new ArrayList<>();

        if (name == null || name.trim().isEmpty()) {
            return matchedUsers;
        }

        String lowerName = name.toLowerCase();

        // 遍历所有用户缓存
        for (Map.Entry<String, Map<String, Object>> entry : userInfoCache.entrySet()) {
            Map<String, Object> user = entry.getValue();
            String userName = (String) user.get("name");

            if (userName != null && userName.toLowerCase().contains(lowerName)) {
                matchedUsers.add(user);
            }
        }

        return matchedUsers;
    }

}