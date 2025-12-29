package com.fit2cloud.fapiao.controller;

import com.fit2cloud.fapiao.dto.response.ApiResponse;
import com.fit2cloud.fapiao.service.DepartmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/department")
@Slf4j
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    /**
     * 获取部门列表
     */
    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> getDepartmentList() {
        try {
            log.info("获取部门列表请求");
            List<Map<String, Object>> departments = departmentService.getDepartmentList();
            return ApiResponse.success(departments);
        } catch (Exception e) {
            log.error("获取部门列表失败", e);
            return ApiResponse.error("获取部门列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据部门ID获取区域信息
     */
    @GetMapping("/getRegion")
    public ApiResponse<String> getRegionByDepartment(@RequestParam String departmentId) {
        try {
            log.info("根据部门获取区域信息, departmentId: {}", departmentId);

            // 添加空值检查
            if (departmentId == null || departmentId.trim().isEmpty()) {
                log.warn("部门ID为空");
                return ApiResponse.error("部门ID不能为空");
            }

            if ("undefined".equals(departmentId)) {
                log.warn("部门ID为undefined");
                return ApiResponse.error("部门ID无效");
            }

            String region = departmentService.getRegionByDepartment(departmentId);
            log.info("部门 {} 对应的区域: {}", departmentId, region);

            // 修复：确保返回正确的数据结构
            return ApiResponse.success(region != null ? region : "");

        } catch (Exception e) {
            log.error("获取区域信息失败", e);
            return ApiResponse.error("获取区域信息失败: " + e.getMessage());
        }
    }
}