// #file src/main/java/com/fit2cloud/fapiao/service/UserDepartmentRelationService.java
package com.fit2cloud.fapiao.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Slf4j
public class UserDepartmentRelationService {

    @Value("${qywechat.address-book-secret:}")
    private String addressBookSecret;

    @Autowired
    private AccessTokenService accessTokenService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private DepartmentService departmentService;

    // 缓存用户-部门关系映射
    private Map<String, List<String>> userToDepartmentsMap = new HashMap<>();
    private Map<String, String> userToMainDepartmentMap = new HashMap<>();
    private long cacheTimestamp;
    private static final long CACHE_DURATION = 30 * 60 * 1000; // 30分钟缓存

    /**
     * 获取用户与部门的对应关系（带缓存）
     */
    public Map<String, List<String>> getUserDepartmentRelations() {
        // 检查缓存是否有效
        if (!userToDepartmentsMap.isEmpty() &&
                System.currentTimeMillis() - cacheTimestamp < CACHE_DURATION) {
            log.info("使用缓存的用户-部门关系数据，用户数量: {}", userToDepartmentsMap.size());
            return new HashMap<>(userToDepartmentsMap);
        }

        log.info("开始获取成员ID列表，使用通讯录同步secret");

        try {
            // 使用通讯录同步secret获取access_token
            String accessToken = accessTokenService.getAccessTokenForAddressBook();
            if (accessToken == null || accessToken.isEmpty()) {
                log.error("无法获取通讯录同步access_token，请检查配置");
                return Collections.emptyMap();
            }

            String url = "https://qyapi.weixin.qq.com/cgi-bin/user/list_id?access_token=" + accessToken;

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("limit", 10000); // 最大限制

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer errcode = (Integer) responseBody.get("errcode");

                if (errcode != null && errcode == 0) {
                    List<Map<String, Object>> deptUserList = (List<Map<String, Object>>) responseBody.get("dept_user");
                    log.info("成功获取到 {} 条用户-部门关系记录", deptUserList != null ? deptUserList.size() : 0);

                    // 清空旧数据
                    userToDepartmentsMap.clear();
                    userToMainDepartmentMap.clear();

                    // 处理分页数据（如果存在next_cursor）
                    String nextCursor = (String) responseBody.get("next_cursor");
                    int totalCount = deptUserList != null ? deptUserList.size() : 0;

                    // 处理当前页数据
                    processDeptUserList(deptUserList);

                    // 如果还有下一页数据，继续获取
                    while (nextCursor != null && !nextCursor.isEmpty()) {
                        log.info("还有下一页数据，游标: {}", nextCursor);
                        Map<String, Object> nextRequestBody = new HashMap<>();
                        nextRequestBody.put("cursor", nextCursor);
                        nextRequestBody.put("limit", 10000);

                        HttpEntity<Map<String, Object>> nextRequestEntity = new HttpEntity<>(nextRequestBody, headers);
                        ResponseEntity<Map> nextResponse = restTemplate.postForEntity(url, nextRequestEntity, Map.class);

                        if (nextResponse.getStatusCode().is2xxSuccessful() && nextResponse.getBody() != null) {
                            Map<String, Object> nextResponseBody = nextResponse.getBody();
                            Integer nextErrcode = (Integer) nextResponseBody.get("errcode");

                            if (nextErrcode != null && nextErrcode == 0) {
                                List<Map<String, Object>> nextDeptUserList = (List<Map<String, Object>>) nextResponseBody.get("dept_user");
                                processDeptUserList(nextDeptUserList);
                                totalCount += nextDeptUserList != null ? nextDeptUserList.size() : 0;

                                nextCursor = (String) nextResponseBody.get("next_cursor");
                            } else {
                                log.error("获取下一页数据失败, errcode: {}", nextErrcode);
                                break;
                            }
                        } else {
                            log.error("获取下一页数据请求失败");
                            break;
                        }
                    }

                    // 确定每个用户的主部门（通常是第一个部门或指定规则）
                    determineMainDepartments();

                    // 更新缓存时间
                    cacheTimestamp = System.currentTimeMillis();

                    log.info("用户-部门关系数据缓存已更新，共 {} 个用户，{} 条关系记录",
                            userToDepartmentsMap.size(), totalCount);

                    // 记录调试信息
                    logUserDepartmentRelations();

                    return new HashMap<>(userToDepartmentsMap);
                } else {
                    String errmsg = (String) responseBody.get("errmsg");
                    log.error("获取成员ID列表失败, errcode: {}, errmsg: {}", errcode, errmsg);
                    return Collections.emptyMap();
                }
            } else {
                log.error("获取成员ID列表请求失败, 状态码: {}", response.getStatusCode());
                return Collections.emptyMap();
            }
        } catch (Exception e) {
            log.error("获取成员ID列表异常", e);
            return Collections.emptyMap();
        }
    }

    /**
     * 处理用户-部门关系列表
     */
    private void processDeptUserList(List<Map<String, Object>> deptUserList) {
        if (deptUserList == null || deptUserList.isEmpty()) {
            return;
        }

        for (Map<String, Object> deptUser : deptUserList) {
            String userId = (String) deptUser.get("userid");
            Object deptObj = deptUser.get("department");

            if (userId != null && !userId.isEmpty() && deptObj != null) {
                String departmentId = deptObj.toString();

                // 添加到用户-部门映射
                userToDepartmentsMap.computeIfAbsent(userId, k -> new ArrayList<>()).add(departmentId);

                log.debug("用户 {} 属于部门 {}", userId, departmentId);
            }
        }
    }

    /**
     * 确定每个用户的主部门
     * 规则：部门ID最小的作为主部门（通常是最上级部门）
     */
    private void determineMainDepartments() {
        for (Map.Entry<String, List<String>> entry : userToDepartmentsMap.entrySet()) {
            String userId = entry.getKey();
            List<String> departments = entry.getValue();

            if (departments != null && !departments.isEmpty()) {
                // 按部门ID排序，取最小的作为主部门
                List<String> sortedDepartments = new ArrayList<>(departments);
                sortedDepartments.sort(Comparator.comparingInt(Integer::parseInt));
                String mainDepartment = sortedDepartments.get(0);

                userToMainDepartmentMap.put(userId, mainDepartment);

                log.debug("用户 {} 的主部门确定为: {}", userId, mainDepartment);
            }
        }
    }

    /**
     * 根据用户ID获取其所有部门ID
     */
    public List<String> getDepartmentsByUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // 确保数据已加载
        if (userToDepartmentsMap.isEmpty()) {
            getUserDepartmentRelations();
        }

        return userToDepartmentsMap.getOrDefault(userId, Collections.emptyList());
    }

    /**
     * 根据用户ID获取主部门ID
     */
    public String getMainDepartmentByUserId(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }

        // 确保数据已加载
        if (userToMainDepartmentMap.isEmpty()) {
            getUserDepartmentRelations();
        }

        return userToMainDepartmentMap.get(userId);
    }

    /**
     * 根据用户ID获取部门结构（用于前端默认值）
     * 优先返回具体业务部门，如果只有区域部门，则查找其下的第一个子部门
     */
    public Map<String, Object> getDepartmentStructureForUser(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            // 获取用户的所有部门ID
            List<String> departmentIds = getDepartmentsByUserId(userId);
            if (departmentIds.isEmpty()) {
                log.warn("用户 {} 没有找到部门信息", userId);
                return getDefaultDepartmentInfo();
            }

            // 获取完整的部门列表
            List<Map<String, Object>> allDepartments = departmentService.getDepartmentList();

            // 查找用户的部门，优先选择具体的业务部门
            String targetDepartmentId = findBestDepartmentForUser(departmentIds, allDepartments);

            if (targetDepartmentId == null) {
                log.warn("未能为用户 {} 找到合适的部门", userId);
                return getDefaultDepartmentInfo();
            }

            // 找到目标部门信息
            Map<String, Object> targetDepartment = null;
            for (Map<String, Object> dept : allDepartments) {
                Integer id = (Integer) dept.get("id");
                if (id != null && targetDepartmentId.equals(id.toString())) {
                    targetDepartment = dept;
                    break;
                }
            }

            if (targetDepartment == null) {
                return getDefaultDepartmentInfo();
            }

            // 构建返回结构
            Map<String, Object> result = new HashMap<>();
            result.put("departmentId", targetDepartmentId);
            result.put("departmentName", targetDepartment.get("name"));
            result.put("fullPath", targetDepartment.get("fullPath"));

            // 获取部门层级信息
            List<String> departmentHierarchy = getDepartmentHierarchy(targetDepartmentId, allDepartments);
            result.put("departmentHierarchy", departmentHierarchy);

            // 获取区域信息
            String region = extractRegionFromDepartment((String) targetDepartment.get("fullPath"), allDepartments);
            result.put("region", region);
            result.put("regionDepartmentId", findRegionDepartmentId(region));

            log.info("用户 {} 的部门结构: ID={}, 名称={}, 路径={}, 区域={}",
                    userId, targetDepartmentId, targetDepartment.get("name"),
                    targetDepartment.get("fullPath"), region);

            return result;
        } catch (Exception e) {
            log.error("获取用户部门结构失败", e);
            return getDefaultDepartmentInfo();
        }
    }

    /**
     * 获取默认的部门信息
     * 默认返回智能体开发部门（ID=7）
     */
    private Map<String, Object> getDefaultDepartmentInfo() {
        try {
            List<Map<String, Object>> allDepartments = departmentService.getDepartmentList();

            // 查找智能体开发部门（ID=7）
            for (Map<String, Object> dept : allDepartments) {
                Integer id = (Integer) dept.get("id");
                if (id != null && id == 7) {
                    Map<String, Object> defaultInfo = new HashMap<>();
                    defaultInfo.put("departmentId", "7");
                    defaultInfo.put("departmentName", "智能体开发");
                    defaultInfo.put("fullPath", dept.get("fullPath"));

                    // 获取层级信息
                    List<String> hierarchy = getDepartmentHierarchy("7", allDepartments);
                    defaultInfo.put("departmentHierarchy", hierarchy);

                    // 提取区域信息
                    String region = extractRegionFromDepartment((String) dept.get("fullPath"), allDepartments);
                    defaultInfo.put("region", region);
                    defaultInfo.put("regionDepartmentId", findRegionDepartmentId(region));

                    log.info("使用默认部门: 智能体开发 (ID=7), 区域: {}", region);
                    return defaultInfo;
                }
            }
        } catch (Exception e) {
            log.error("获取默认部门信息失败", e);
        }

        // 如果找不到，返回硬编码的默认值
        Map<String, Object> fallbackInfo = new HashMap<>();
        fallbackInfo.put("departmentId", "7");
        fallbackInfo.put("departmentName", "智能体开发");
        fallbackInfo.put("fullPath", "华南区域 - 智能体开发");
        fallbackInfo.put("departmentHierarchy", Arrays.asList("华南区域", "智能体开发"));
        fallbackInfo.put("region", "华南区域");
        fallbackInfo.put("regionDepartmentId", "4");
        return fallbackInfo;
    }

    /**
     * 为用户找到最合适的部门
     * 规则：
     * 1. 优先选择非区域部门的业务部门
     * 2. 如果只有区域部门，选择该区域下的第一个子部门
     * 3. 如果区域部门也没有子部门，则使用区域部门本身
     */
    private String findBestDepartmentForUser(List<String> userDepartmentIds,
                                             List<Map<String, Object>> allDepartments) {
        // 构建部门映射
        Map<Integer, Map<String, Object>> departmentMap = new HashMap<>();
        Map<Integer, List<Map<String, Object>>> parentToChildrenMap = new HashMap<>();

        for (Map<String, Object> dept : allDepartments) {
            Integer id = (Integer) dept.get("id");
            Integer parentId = (Integer) dept.get("parentid");

            departmentMap.put(id, dept);

            if (parentId != null) {
                parentToChildrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(dept);
            }
        }

        // 首先检查用户是否有具体的业务部门（非区域部门）
        for (String deptIdStr : userDepartmentIds) {
            try {
                Integer deptId = Integer.parseInt(deptIdStr);
                Map<String, Object> dept = departmentMap.get(deptId);
                if (dept != null) {
                    String deptName = (String) dept.get("name");
                    // 如果部门名称不包含"区域"关键词，认为是业务部门
                    if (!isRegionDepartment(deptName)) {
                        log.info("用户有具体的业务部门: {} (ID={})", deptName, deptId);
                        return deptIdStr;
                    }
                }
            } catch (NumberFormatException e) {
                // 忽略格式错误的部门ID
            }
        }

        // 如果用户只有区域部门，选择区域下的第一个子部门
        for (String deptIdStr : userDepartmentIds) {
            try {
                Integer deptId = Integer.parseInt(deptIdStr);
                Map<String, Object> dept = departmentMap.get(deptId);
                if (dept != null) {
                    String deptName = (String) dept.get("name");
                    // 如果是区域部门，查找其子部门
                    if (isRegionDepartment(deptName)) {
                        List<Map<String, Object>> children = parentToChildrenMap.get(deptId);
                        if (children != null && !children.isEmpty()) {
                            // 返回第一个子部门
                            Integer childId = (Integer) children.get(0).get("id");
                            log.info("用户只有区域部门 {}，选择其子部门: {} (ID={})",
                                    deptName, children.get(0).get("name"), childId);
                            return childId.toString();
                        }
                    }
                }
            } catch (NumberFormatException e) {
                // 忽略格式错误的部门ID
            }
        }

        // 如果以上都不满足，返回第一个部门ID
        return userDepartmentIds.get(0);
    }

    /**
     * 判断是否是区域部门
     */
    private boolean isRegionDepartment(String departmentName) {
        if (departmentName == null) {
            return false;
        }

        String lowerName = departmentName.toLowerCase();
        return lowerName.contains("区域") ||
                lowerName.contains("大区") ||
                lowerName.contains("片区") ||
                lowerName.contains("地区") ||
                departmentName.endsWith("区域");
    }

    /**
     * 获取部门层级信息
     */
    private List<String> getDepartmentHierarchy(String departmentId, List<Map<String, Object>> allDepartments) {
        List<String> hierarchy = new ArrayList<>();
        String currentId = departmentId;

        Map<Integer, Map<String, Object>> departmentMap = new HashMap<>();
        for (Map<String, Object> dept : allDepartments) {
            Integer id = (Integer) dept.get("id");
            departmentMap.put(id, dept);
        }

        try {
            Integer currentDeptId = Integer.parseInt(currentId);
            while (currentDeptId != null) {
                Map<String, Object> currentDept = departmentMap.get(currentDeptId);
                if (currentDept == null) {
                    break;
                }

                hierarchy.add(0, (String) currentDept.get("name"));

                Object parentIdObj = currentDept.get("parentid");
                if (parentIdObj == null || parentIdObj.toString().equals("0") ||
                        parentIdObj.toString().equals("1")) {
                    break;
                }

                currentDeptId = (Integer) parentIdObj;
            }
        } catch (NumberFormatException e) {
            log.error("部门ID格式错误: {}", departmentId);
        }

        return hierarchy;
    }

    /**
     * 从部门路径中提取区域信息（改进版）
     */
    private String extractRegionFromDepartment(String fullPath, List<Map<String, Object>> allDepartments) {
        if (fullPath == null || fullPath.isEmpty()) {
            return "";
        }

        // 按分隔符分割
        String[] parts = fullPath.split(" - ");

        // 查找包含区域关键词的部分
        for (String part : parts) {
            if (isRegionDepartment(part)) {
                return part;
            }
        }

        // 如果没有找到区域关键词，检查部门层级
        // 假设第二级部门是区域（第一级是公司/根部门）
        if (parts.length >= 2) {
            // 检查第二级是否是已知的区域
            String secondLevel = parts[1];
            for (Map<String, Object> dept : allDepartments) {
                String name = (String) dept.get("name");
                if (secondLevel.equals(name) && isRegionDepartment(name)) {
                    return secondLevel;
                }
            }
        }

        // 如果都没有找到，返回空
        return "";
    }

    /**
     * 判断是否包含区域关键词
     */
    private boolean containsRegionKeywords(String text) {
        if (text == null) {
            return false;
        }

        String lowerText = text.toLowerCase();
        return lowerText.contains("区域") ||
                lowerText.contains("区") ||
                lowerText.contains("大区") ||
                lowerText.contains("片区") ||
                lowerText.contains("地区");
    }

    /**
     * 根据区域名称找到对应的区域部门ID
     */
    private String findRegionDepartmentId(String region) {
        if (region == null || region.isEmpty()) {
            return null;
        }

        // 这里可以调用DepartmentService的方法，或者实现自己的逻辑
        try {
            List<Map<String, Object>> departments = departmentService.getDepartmentList();
            for (Map<String, Object> dept : departments) {
                String name = (String) dept.get("name");
                Integer id = (Integer) dept.get("id");
                if (region.equals(name)) {
                    return id.toString();
                }
            }
        } catch (Exception e) {
            log.error("查找区域部门ID失败", e);
        }

        return null;
    }

    /**
     * 强制刷新缓存
     */
    public void refreshCache() {
        log.info("强制刷新用户-部门关系缓存");
        userToDepartmentsMap.clear();
        userToMainDepartmentMap.clear();
        cacheTimestamp = 0;
        getUserDepartmentRelations();
    }

    /**
     * 获取缓存状态
     */
    public Map<String, Object> getCacheStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("hasCache", !userToDepartmentsMap.isEmpty());
        status.put("userCount", userToDepartmentsMap.size());
        status.put("relationCount", userToDepartmentsMap.values().stream()
                .mapToInt(List::size)
                .sum());
        status.put("cacheTimestamp", cacheTimestamp);
        status.put("cacheAge", cacheTimestamp > 0 ?
                (System.currentTimeMillis() - cacheTimestamp) / 1000 + "秒" : "无缓存");
        status.put("isValid", cacheTimestamp > 0 &&
                (System.currentTimeMillis() - cacheTimestamp < CACHE_DURATION));
        return status;
    }

    /**
     * 记录用户-部门关系用于调试
     */
    private void logUserDepartmentRelations() {
        log.info("=== 用户-部门关系分析 ===");
        int count = 0;
        for (Map.Entry<String, List<String>> entry : userToDepartmentsMap.entrySet()) {
            if (count < 10) { // 只记录前10个用户用于调试
                String mainDept = userToMainDepartmentMap.get(entry.getKey());
                log.info("用户: {}, 部门数: {}, 主部门: {}",
                        entry.getKey(), entry.getValue().size(), mainDept);
            }
            count++;
        }
        log.info("共 {} 个用户，{} 个有主部门",
                userToDepartmentsMap.size(), userToMainDepartmentMap.size());
        log.info("=== 用户-部门关系分析结束 ===");
    }
}