package com.fit2cloud.fapiao.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DepartmentService {

    @Autowired
    private AccessTokenService accessTokenService;

    @Autowired
    private RestTemplate restTemplate;

    private List<Map<String, Object>> cachedDepartments;
    private long cacheTimestamp;
    private static final long CACHE_DURATION = 5 * 60 * 1000; // 5分钟缓存

    // 添加以下成员变量
    private Map<String, String> userIdToNameCache = new HashMap<>();
    private long userCacheTimestamp;
    private static final long USER_CACHE_DURATION = 60 * 60 * 1000; // 1小时缓存

    /**
     * 根据用户ID获取用户姓名（带缓存）
     */
    public String getUserNameById(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return userId;
        }

        // 检查缓存
        if (userIdToNameCache.containsKey(userId)) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - userCacheTimestamp < USER_CACHE_DURATION) {
                log.debug("使用缓存的用户姓名: {} -> {}", userId, userIdToNameCache.get(userId));
                return userIdToNameCache.get(userId);
            }
        }

        log.debug("未找到用户 {} 的缓存，需要获取", userId);
        return null; // 返回null表示需要外部获取
    }

    /**
     * 缓存用户信息
     */
    public void cacheUserInfo(String userId, String userName) {
        if (userId != null && userName != null) {
            userIdToNameCache.put(userId, userName);
            userCacheTimestamp = System.currentTimeMillis();
            log.debug("缓存用户信息: {} -> {}", userId, userName);
        }
    }

    /**
     * 批量缓存用户信息
     */
    public void batchCacheUserInfo(Map<String, String> userInfos) {
        if (userInfos != null && !userInfos.isEmpty()) {
            userIdToNameCache.putAll(userInfos);
            userCacheTimestamp = System.currentTimeMillis();
            log.info("批量缓存 {} 个用户信息", userInfos.size());
        }
    }

    /**
     * 获取用户缓存状态
     */
    public Map<String, Object> getUserCacheStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("cacheSize", userIdToNameCache.size());
        status.put("cacheTimestamp", userCacheTimestamp);
        status.put("cacheAge", userCacheTimestamp > 0 ?
                (System.currentTimeMillis() - userCacheTimestamp) / 1000 + "秒" : "无缓存");
        status.put("isValid", userCacheTimestamp > 0 &&
                (System.currentTimeMillis() - userCacheTimestamp < USER_CACHE_DURATION));
        return status;
    }

    /**
     * 清空用户缓存
     */
    public void clearUserCache() {
        userIdToNameCache.clear();
        userCacheTimestamp = 0;
        log.info("已清空用户缓存");
    }

    /**
     * 获取部门列表
     */
    public List<Map<String, Object>> getDepartmentList() {
        // 检查缓存是否有效
        if (cachedDepartments != null &&
                System.currentTimeMillis() - cacheTimestamp < CACHE_DURATION) {
            log.info("使用缓存的部门列表，数量: {}", cachedDepartments.size());
            return new ArrayList<>(cachedDepartments); // 返回副本避免外部修改
        }

        try {
            String accessToken = accessTokenService.getAccessTokenForApproval();
            String url = "https://qyapi.weixin.qq.com/cgi-bin/department/list?access_token=" + accessToken;

            log.info("调用企业微信部门列表接口, accessToken: {}", accessToken.substring(0, 10) + "...");

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer errcode = (Integer) responseBody.get("errcode");

                if (errcode != null && errcode == 0) {
                    List<Map<String, Object>> departments = (List<Map<String, Object>>) responseBody.get("department");
                    log.info("从企业微信获取到 {} 个部门", departments != null ? departments.size() : 0);

                    if (departments != null && !departments.isEmpty()) {
                        List<Map<String, Object>> result = buildDepartmentTree(departments);

                        // 更新缓存
                        cachedDepartments = new ArrayList<>(result);
                        cacheTimestamp = System.currentTimeMillis();

                        // 记录部门结构用于调试
                        logDepartmentStructure(result);

                        log.info("部门列表缓存已更新，数量: {}", result.size());
                        return result;
                    } else {
                        log.warn("企业微信返回的部门列表为空");
                        // 清空缓存
                        cachedDepartments = new ArrayList<>();
                        cacheTimestamp = System.currentTimeMillis();
                        return new ArrayList<>();
                    }
                } else {
                    String errmsg = (String) responseBody.get("errmsg");
                    log.error("获取部门列表失败, errcode: {}, errmsg: {}", errcode, errmsg);
                    // 如果缓存存在且未过期，继续使用缓存
                    if (cachedDepartments != null && !cachedDepartments.isEmpty()) {
                        log.warn("企业微信接口失败，使用缓存的部门数据");
                        return new ArrayList<>(cachedDepartments);
                    }
                    throw new RuntimeException("获取部门列表失败: " + errmsg);
                }
            } else {
                // 如果缓存存在且未过期，继续使用缓存
                if (cachedDepartments != null && !cachedDepartments.isEmpty()) {
                    log.warn("企业微信请求失败，使用缓存的部门数据");
                    return new ArrayList<>(cachedDepartments);
                }
                log.error("获取部门列表请求失败, 状态码: {}", response.getStatusCode());
                throw new RuntimeException("获取部门列表请求失败");
            }
        } catch (Exception e) {
            // 如果缓存存在且未过期，继续使用缓存
            if (cachedDepartments != null && !cachedDepartments.isEmpty()) {
                log.warn("获取部门列表异常，使用缓存的部门数据", e);
                return new ArrayList<>(cachedDepartments);
            }
            log.error("获取部门列表异常", e);
            throw new RuntimeException("获取部门列表异常: " + e.getMessage());
        }
    }

    /**
     * 强制刷新部门缓存
     */
    public void refreshDepartmentCache() {
        log.info("强制刷新部门缓存");
        cachedDepartments = null;
        cacheTimestamp = 0;
        getDepartmentList(); // 重新加载
    }

    /**
     * 获取缓存状态
     */
    public Map<String, Object> getCacheStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("hasCache", cachedDepartments != null);
        status.put("cacheSize", cachedDepartments != null ? cachedDepartments.size() : 0);
        status.put("cacheTimestamp", cacheTimestamp);
        status.put("cacheAge", cachedDepartments != null ?
                (System.currentTimeMillis() - cacheTimestamp) / 1000 + "秒" : "无缓存");
        status.put("isValid", cachedDepartments != null &&
                (System.currentTimeMillis() - cacheTimestamp < CACHE_DURATION));
        return status;
    }

    /**
     * 记录部门结构用于调试
     */
    private void logDepartmentStructure(List<Map<String, Object>> departments) {
        log.info("=== 部门结构分析 ===");
        for (Map<String, Object> dept : departments) {
            Integer id = (Integer) dept.get("id");
            String name = (String) dept.get("name");
            Integer parentId = (Integer) dept.get("parentid");
            String fullPath = (String) dept.get("fullPath");

            log.info("部门: ID={}, 名称={}, 父ID={}, 路径={}", id, name, parentId, fullPath);
        }
        log.info("=== 部门结构分析结束 ===");
    }

    /**
     * 构建部门树形结构并计算完整路径
     */
    private List<Map<String, Object>> buildDepartmentTree(List<Map<String, Object>> departments) {
        // 构建部门ID到部门的映射
        Map<Integer, Map<String, Object>> departmentMap = new HashMap<>();
        for (Map<String, Object> dept : departments) {
            Integer id = (Integer) dept.get("id");
            departmentMap.put(id, dept);
        }

        // 计算每个部门的完整路径
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> dept : departments) {
            Map<String, Object> departmentInfo = new HashMap<>(dept);
            String fullPath = buildDepartmentPath(dept, departmentMap);
            departmentInfo.put("fullPath", fullPath);
            result.add(departmentInfo);
        }

        log.info("构建部门树完成，共 {} 个部门", result.size());
        return result;
    }

    /**
     * 构建部门完整路径
     */
    private String buildDepartmentPath(Map<String, Object> department, Map<Integer, Map<String, Object>> departmentMap) {
        List<String> pathParts = new ArrayList<>();
        Map<String, Object> currentDept = department;

        while (currentDept != null) {
            String name = (String) currentDept.get("name");
            if (name != null && !name.isEmpty()) {
                pathParts.add(0, name);
            } else {
                // 如果name为空，使用ID作为备选
                Integer id = (Integer) currentDept.get("id");
                pathParts.add(0, "部门" + id);
            }

            Integer parentId = (Integer) currentDept.get("parentid");
            if (parentId == null || parentId == 0 || parentId == 1) {
                break; // 到达根部门
            }

            currentDept = departmentMap.get(parentId);
            if (currentDept == null) {
                break; // 父部门不存在
            }
        }

        String path = String.join(" - ", pathParts);
        log.debug("部门路径: {}", path);
        return path;
    }

    /**
     * 根据部门ID获取区域信息
     */
    public String getRegionByDepartment(String departmentId) {
        try {
            log.info("开始根据部门ID获取区域信息: {}", departmentId);

            // 添加空值检查
            if (departmentId == null || departmentId.trim().isEmpty()) {
                log.warn("部门ID为空");
                return "";
            }

            if ("undefined".equals(departmentId)) {
                log.warn("部门ID为undefined");
                return "";
            }

            List<Map<String, Object>> departments = getDepartmentList();
            Map<Integer, Map<String, Object>> departmentMap = new HashMap<>();

            // 构建映射
            for (Map<String, Object> dept : departments) {
                Integer id = (Integer) dept.get("id");
                departmentMap.put(id, dept);
            }

            // 查找指定部门
            Integer targetId;
            try {
                targetId = Integer.parseInt(departmentId);
            } catch (NumberFormatException e) {
                log.error("部门ID格式错误: {}", departmentId);
                return "";
            }

            Map<String, Object> targetDept = departmentMap.get(targetId);

            if (targetDept == null) {
                log.warn("未找到部门: {}", departmentId);
                return "";
            }

            // 获取区域：根目录后一级
            String region = extractRegionFromPath(targetDept, departmentMap);
            log.info("部门 {} 对应的区域: {}", departmentId, region);
            return region;

        } catch (Exception e) {
            log.error("根据部门获取区域信息失败", e);
            return "";
        }
    }

    /**
     * 从部门路径中提取区域信息
     */
    private String extractRegionFromPath(Map<String, Object> department, Map<Integer, Map<String, Object>> departmentMap) {
        List<String> pathParts = new ArrayList<>();
        Map<String, Object> currentDept = department;

        // 构建路径
        while (currentDept != null) {
            String name = (String) currentDept.get("name");
            if (name != null && !name.isEmpty()) {
                pathParts.add(0, name);
            } else {
                Integer id = (Integer) currentDept.get("id");
                pathParts.add(0, "部门" + id);
            }

            Integer parentId = (Integer) currentDept.get("parentid");
            if (parentId == null || parentId == 0 || parentId == 1) {
                break; // 到达根部门
            }

            currentDept = departmentMap.get(parentId);
            if (currentDept == null) {
                break; // 父部门不存在
            }
        }

        log.debug("部门完整路径: {}", String.join(" - ", pathParts));

        // 通用区域提取策略：
        // 1. 首先查找包含"区域"、"区"、"大区"等关键词的部门
        // 2. 如果没有找到，使用第二级部门作为区域（假设第一级是公司，第二级是区域）
        // 3. 如果只有一级，使用该部门名称

        // 策略1：查找区域关键词
        for (String part : pathParts) {
            if (containsRegionKeywords(part)) {
                log.info("通过关键词识别区域: {}", part);
                return part;
            }
        }

        // 策略2：使用第二级部门
        if (pathParts.size() >= 2) {
            String secondLevel = pathParts.get(1);
            log.info("使用第二级部门作为区域: {}", secondLevel);
            return secondLevel;
        }

        // 策略3：使用第一级部门
        if (pathParts.size() >= 1) {
            String firstLevel = pathParts.get(0);
            log.info("使用第一级部门作为区域: {}", firstLevel);
            return firstLevel;
        }

        // 如果没有找到区域部门，返回空
        log.warn("无法提取区域信息，路径为空");
        return "";
    }

    /**
     * 判断部门名称是否包含区域关键词
     */
    private boolean containsRegionKeywords(String departmentName) {
        if (departmentName == null) {
            return false;
        }

        String lowerName = departmentName.toLowerCase();
        return lowerName.contains("区域") ||
                lowerName.contains("区") ||
                lowerName.contains("大区") ||
                lowerName.contains("片区") ||
                lowerName.contains("地区") ||
                lowerName.contains("地域");
    }
}