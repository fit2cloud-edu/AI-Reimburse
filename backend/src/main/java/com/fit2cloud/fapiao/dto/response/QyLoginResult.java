package com.fit2cloud.fapiao.dto.response;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class QyLoginResult {
    private String userId;
    private String userName; // 用户真实姓名
    private String sessionKey;
    private String userTicket;// 用户票据（可选）
    private String corpid;
    private Integer expiresIn;// 有效期（秒）
    private String avatar; // 新增：用户头像
    private String loginType; // 新增：登录类型（web/mini）

    private String department;
    private String region;
    private List<Integer> departmentIds;

    // 新增：部门结构信息
    private Map<String, Object> departmentStructure; // 完整的部门结构信息
    private String departmentId;                    // 主要部门ID
    private String departmentName;                  // 主要部门名称
    private String departmentFullPath;              // 部门完整路径
    private List<String> departmentHierarchy;       // 部门层级列表
    private String regionDepartmentId;              // 区域部门ID

    // 无参构造器
    public QyLoginResult() {}

    // 全参构造器
    public QyLoginResult(String userId, String userName, String sessionKey, String userTicket,
                         String corpid, Integer expiresIn, String avatar, String loginType) {
        this.userId = userId;
        this.userName = userName;
        this.sessionKey = sessionKey;
        this.userTicket = userTicket;
        this.corpid = corpid;
        this.expiresIn = expiresIn;
        this.avatar = avatar;
        this.loginType = loginType;
    }

    // 新增：统一返回 userInfo 对象
    public Map<String, String> getUserInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("userid", this.userId);
        info.put("name", this.userName != null ? this.userName : this.userId);
        info.put("avatar", this.avatar != null ? this.avatar : "");
        info.put("loginType", this.loginType != null ? this.loginType : "web");
        return info;
    }
}