// #file src/main/java/com/fit2cloud/fapiao/controller/UserInfoController.java
package com.fit2cloud.fapiao.controller;

import com.fit2cloud.fapiao.service.UserInfoService;
import com.fit2cloud.fapiao.service.UserDepartmentRelationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user-info")
@Slf4j
public class UserInfoController {

    @Autowired
    private UserInfoService userInfoService;

    @Autowired
    private UserDepartmentRelationService userDepartmentRelationService;

    /**
     * 获取成员详细信息
     */
    @GetMapping("/{userId}")
    public Map<String, Object> getUserInfo(@PathVariable String userId) {
        log.info("获取用户 {} 的详细信息", userId);
        return userInfoService.getUserInfo(userId);
    }

    /**
     * 批量获取成员信息
     */
    @PostMapping("/batch")
    public List<Map<String, Object>> batchGetUserInfo(@RequestBody List<String> userIds) {
        log.info("批量获取用户信息，用户数量: {}", userIds.size());
        return userInfoService.batchGetUserInfo(userIds);
    }

    /**
     * 根据部门ID获取部门成员
     */
    @GetMapping("/department/{departmentId}")
    public List<Map<String, Object>> getUsersByDepartment(@PathVariable String departmentId) {
        log.info("获取部门 {} 下的所有成员", departmentId);
        return userInfoService.getUsersByDepartment(departmentId);
    }

    /**
     * 搜索用户
     */
    @GetMapping("/search")
    public List<Map<String, Object>> searchUsers(@RequestParam String keyword) {
        log.info("搜索用户，关键词: {}", keyword);
        return userInfoService.searchUsers(keyword);
    }

    /**
     * 验证人名是否为企业成员
     */
    @GetMapping("/verify")
    public Map<String, Object> verifyEnterpriseMember(@RequestParam String name) {
        log.info("验证人名是否为企业成员: {}", name);
        return userInfoService.verifyEnterpriseMember(name);
    }

    /**
     * 获取用户的完整信息（部门信息 + 用户信息）
     */
    @GetMapping("/full/{userId}")
    public Map<String, Object> getUserFullInfo(@PathVariable String userId) {
        log.info("获取用户 {} 的完整信息", userId);

        Map<String, Object> result = new java.util.HashMap<>();

        // 获取部门信息
        Map<String, Object> departmentInfo = userDepartmentRelationService.getDepartmentStructureForUser(userId);
        result.putAll(departmentInfo);

        // 获取用户详细信息
        try {
            Map<String, Object> userInfo = userInfoService.getUserInfo(userId);
            result.putAll(userInfo);
        } catch (Exception e) {
            log.warn("获取用户 {} 详细信息失败: {}", userId, e.getMessage());
            result.put("userInfoError", e.getMessage());
        }

        return result;
    }

    /**
     * 刷新用户信息缓存
     */
    @PostMapping("/refresh-cache")
    public Map<String, Object> refreshUserInfoCache(@RequestParam(required = false) String userId) {
        log.info("刷新用户信息缓存，用户ID: {}", userId != null ? userId : "所有用户");

        userInfoService.refreshUserInfoCache(userId);

        return Map.of(
                "success", true,
                "message", userId != null ? "用户 " + userId + " 信息缓存已刷新" : "所有用户信息缓存已刷新"
        );
    }
}