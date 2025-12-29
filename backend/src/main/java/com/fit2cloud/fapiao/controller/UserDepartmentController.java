package com.fit2cloud.fapiao.controller;

import com.fit2cloud.fapiao.service.UserDepartmentRelationService;
import com.fit2cloud.fapiao.service.WeComApprovalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user-department")
@Slf4j
public class UserDepartmentController {

    @Autowired
    private UserDepartmentRelationService userDepartmentRelationService;

    @Autowired
    private WeComApprovalService weComApprovalService;

    /**
     * 获取用户的部门结构信息（用于前端默认值）
     */
    @GetMapping("/info/{userId}")
    public Map<String, Object> getUserDepartmentInfo(@PathVariable String userId) {
        log.info("获取用户 {} 的部门信息", userId);
        return weComApprovalService.getUserDepartmentInfo(userId);
    }

    /**
     * 刷新用户-部门关系缓存
     */
    @PostMapping("/refresh-cache")
    public Map<String, Object> refreshCache() {
        log.info("手动刷新用户-部门关系缓存");
        userDepartmentRelationService.refreshCache();
        return Map.of("success", true, "message", "缓存刷新成功");
    }

    /**
     * 获取缓存状态
     */
    @GetMapping("/cache-status")
    public Map<String, Object> getCacheStatus() {
        return userDepartmentRelationService.getCacheStatus();
    }
}