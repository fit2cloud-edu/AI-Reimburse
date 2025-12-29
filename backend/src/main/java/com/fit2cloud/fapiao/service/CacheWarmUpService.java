package com.fit2cloud.fapiao.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class CacheWarmUpService implements ApplicationRunner {

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private UserInfoService userInfoService;

    @Autowired
    private UserDepartmentRelationService userDepartmentRelationService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("开始预热系统缓存...");

        log.info("预热部门缓存...");
        // 部门服务会自动检查缓存有效性

        log.info("预热用户-部门关系缓存...");
        // 用户-部门关系服务会自动检查缓存有效性

        log.info("预热用户信息缓存...");
        // 修改这里，让UserInfoService自己决定是否需要重新加载
        // 不再直接调用 loadAllUserInfo()

        log.info("系统缓存预热完成");
    }

    /**
     * 应用启动时预热缓存
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpCacheOnStartup() {
        log.info("开始预热系统缓存...");

        try {
            // 1. 预热部门缓存
            log.info("预热部门缓存...");
            departmentService.getDepartmentList();

            // 2. 预热用户-部门关系缓存
            log.info("预热用户-部门关系缓存...");
            userDepartmentRelationService.getUserDepartmentRelations();

            // 3. 预热用户信息缓存（获取前100个用户）
            log.info("预热用户信息缓存...");
            // 这里可以调用一个专门的方法来获取并缓存用户信息
            userInfoService.loadAllUserInfo();

            log.info("系统缓存预热完成");
        } catch (Exception e) {
            log.error("预热缓存失败", e);
        }
    }

    /**
     * 定时刷新缓存（每小时一次）
     */
    @Scheduled(fixedDelay = 60 * 60 * 1000) // 1小时
    public void refreshCachePeriodically() {
        log.info("开始定时刷新缓存...");

        try {
            // 刷新部门缓存
            departmentService.refreshDepartmentCache();

            // 刷新用户-部门关系缓存
            userDepartmentRelationService.refreshCache();

            log.info("定时刷新缓存完成");
        } catch (Exception e) {
            log.error("定时刷新缓存失败", e);
        }
    }
}