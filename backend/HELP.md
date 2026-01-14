# 项目介绍

        一个基于Spring Boot开发的发票管理系统，主要用于企业内部的发票报销流程管理，
        集成了企业微信审批功能，提供发票验证、报销申请提交等功能。

# 技术栈

      框架：Spring Boot 3.4.11
      开发语言：Java 17
      构建工具：Maven
      企业微信集成：支持企业微信登录、审批流程
      文件处理：支持上传PDF、图片等发票文件
      其他依赖：Lombok、Spring Boot Actuator

# 快速开始

环境要求：

      JDK 17+
      Maven 3.6+
      MySQL 8.0+

# 构建与运行

## 运行项目

      bash
      mvn clean package
      java -jar target/Fapiao-0.0.1-SNAPSHOT.war
      
      或者使用Maven直接运行：
      bash 
      mvn spring-boot:run

## 访问应用

    应用将在http://localhost:8888/api启动

## 健康检查接口：

    http://localhost:8888/api/qywechat/health

## 项目结构

      ├── src/main/java/com/fit2cloud/fapiao/
      │   ├── FapiaoApplication.java        # 应用入口类
      │   ├── ServletInitializer.java       # Servlet初始化类
      │   ├── component/                    # 组件
      │   ├── config/                       # 配置类
      │   ├── controller/                   # 控制器
      │   ├── dto/                          # 数据传输对象
      │   ├── entity/                       # 实体类
      │   ├── exception/                    # 异常处理
      │   ├── interceptor/                  # 拦截器
      │   ├── repository/                   # 数据访问层
      │   ├── service/                      # 业务逻辑层
      │   └── util/                         # 工具类
      ├── src/main/resources/
      │   ├── application.properties        # 配置文件
      │   ├── application.yml               # YAML配置文件
      │   ├── static/                       # 静态资源
      │   └── templates/                    # 模板文件

# 主要配置

在application.yml中配置以下关键信息：

## 企业微信配置

      yaml
         Apply
            qywechat:
            approval:
            template-id.daily: <日常报销审批模板ID>
            template-id.travel: <差旅报销审批模板ID>
            template-id.business-trip: <出差审批模板ID>
            template-id.travel-subsidy: <差旅补贴审批模板ID>
            corpid: <企业微信企业ID>
            agentSecret: <企业微信自建应用密钥>
            address-book-secret: <通讯录密钥>
            agentid: <应用ID>
            app-id: <企业微信企业ID，用于信息比对>

## MaxKB智能体配置

      yaml
         Apply
            maxkb:
            base-url: <MaxKB API地址>
            api-key: <MaxKB API密钥>

## 发票验证配置

      yaml
         Apply
            invoice:
               verification:
                  enabled: true
                  api:
                     host: https://fapiao.market.alicloudapi.com
                     path: /v2/invoice/query
                     appcode: <阿里云API AppCode>

## 发票查重配置

      yaml
         Apply
            invoice:
               duplicate:
                  check:
                     enabled: true                          # 查重功能开关
                     strategy: STRICT                       # 查重策略：STRICT|NORMAL|USER

## 文件配置

      yaml
         Apply
            file:
               allowed-types: jpg,jpeg,png,pdf,bmp
               max-size: 10485760

## 其他配置

      1.目前后端提交审批流程为：个人提交->本人驳回或同意->部门领导审批
      
      2.除了修改审批模板外，还要修改Service(WeComApprovalService、WeChatWorkFileService)
        代码中的审批模板控件ID，确保与企业微信配置一致。

# 主要功能模块

## 1. 企业微信集成

### 实现服务

      QyWechatService：企业微信登录核心服务
         实现getUserInfoByCode方法，处理企业微信登录流程
         集成企业微信code2Session接口获取用户信息
         与UserDepartmentRelationService协作获取用户部门结构

      AccessTokenService：企业微信Access Token管理服务
         实现不同类型Token（agent、approval、address-book）的获取和缓存
         使用ConcurrentHashMap实现线程安全的Token缓存
         支持Token自动刷新（提前5分钟）

      QyWechatWebService：企业微信网页版集成服务
         处理企业微信网页授权登录
         获取用户详细信息（姓名、头像、部门等）
         支持JS-SDK配置生成

### 核心功能

      1.登录功能：支持企业微信扫码登录
      2.Session管理：提供Session有效性检查
      3.审批流程：集成企业微信审批模板
      4.部门和用户关系管理：获取和维护用户部门结构

## 2. 报销管理

### 实现服务

      ReimbursementService：报销申请核心服务
         实现submitReimbursement方法处理报销申请提交
         支持两种报销类型：日常报销、客成差旅报销
         与多个审批服务协作（WeComApprovalService、BusinessTripService、TravelSubsidyService）

      WeComApprovalService：企业微信审批服务
         实现submitApproval方法提交报销申请到企业微信审批
         支持不同类型的审批模板（日常、差旅、出差、差旅补贴）
         处理审批结果查询和状态管理

      BusinessTripService：出差申请服务
         实现submitBusinessTripApproval方法提交出差申请
         管理出差申请的审批流程

      TravelSubsidyService：差旅补贴服务
         计算差旅补贴金额
         提交差旅补贴申请到企业微信审批

### 核心功能

      1.报销申请提交：支持两种报销类型（日常报销、客成差旅报销）
      2.发票管理：批量上传和管理发票
      3.审批流程：自动触发企业微信审批
      4.出差申请和补贴计算：完整的差旅流程支持

## 3. 发票验证

###    实现服务

      InvoiceVerificationService：发票验证核心服务
         实现verifyInvoice方法验证发票真伪
         调用阿里云API进行发票验证
         处理发票信息提取和格式转换

### 核心功能

      1.真伪验证：调用阿里云API验证发票真伪
      2.信息提取：自动提取发票关键信息（号码、日期、金额等）
      3.验证结果记录：保存验证结果
      4.格式处理：支持多种日期格式和金额格式的转换

## 4. 发票查重

### 实现服务

      InvoiceDuplicateCheckService：发票查重核心服务
         实现checkDuplicate方法检查发票是否重复
         支持多种查重策略（STRICT、NORMAL、USER）
         提供发票记录管理和状态更新功能

      InvoiceDuplicateCheckRepository：发票查重数据访问层
         提供多种查重查询方法
         支持发票记录的增删改查操作

      MySQL数据库存储经该系统提交的发票的信息
         数据库表建表语句如下:  
                  CREATE TABLE `invoice_duplicate_check` (
                  `id` bigint NOT NULL AUTO_INCREMENT,
                  `invoice_number` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发票号码',
                  `invoice_date` date NOT NULL COMMENT '开票日期',
                  `total_amount` decimal(10,2) DEFAULT NULL COMMENT '发票金额',
                  `user_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提交用户ID',
                  `submit_time` datetime NOT NULL COMMENT '提交时间',
                  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'SUBMITTED' COMMENT '状态：SUBMITTED/APPROVED/REJECTED',
                  `created_time` datetime DEFAULT CURRENT_TIMESTAMP,
                  `updated_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (`id`),
                  UNIQUE KEY `uk_invoice_identifier` (`invoice_number`,`invoice_date`,`user_id`),
                  UNIQUE KEY `UKpyuty18s9esx7qwbh1oxgvew3` (`invoice_number`,`invoice_date`,`user_id`),
                  KEY `idx_invoice_number_date` (`invoice_number`,`invoice_date`),
                  KEY `idx_user_submit_time` (`user_id`,`submit_time`)
                ) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发票查重记录表'

### 核心功能

      1.查重检查：支持多种查重策略，防止重复报销
         - STRICT策略：检查是否存在相同发票号码和开票日期的记录
         - NORMAL策略：检查是否存在相同发票号码和近似金额的记录
         - USER策略：检查同一用户是否重复提交相同发票
      2.灵活配置：支持通过配置开关启用/禁用查重功能
      3.智能解析：支持多种日期和金额格式的自动解析
      4.状态管理：支持更新发票状态（SUBMITTED、REJECTED等）
      5.多环节集成：在发票识别和报销申请提交两个环节进行查重检查

## 5. 文件管理

### 实现服务

      FileUploadService：文件上传服务
         处理发票文件上传
         支持PDF、图片等格式
         与OSS服务集成

      WedriveFileAdapter：企业微信微盘文件适配器
         处理企业微信微盘文件
         支持文件下载和转换

### 核心功能

      1.文件上传：支持PDF、图片等格式
      2.OSS集成：支持OSS文件存储
      3.文件类型验证：限制允许上传的文件类型
      4.企业微信微盘集成：支持从微盘获取文件

## 6. 部门和用户管理

### 实现服务

      DepartmentService：部门管理服务
         获取部门列表和树形结构
         支持部门缓存（5分钟）
         实现区域信息提取

      UserDepartmentRelationService：用户部门关系服务
         管理用户与部门的关系
         获取用户的部门结构
         支持部门路径构建

### 核心功能

      1.部门结构管理：获取和构建部门树形结构
      2.用户部门关系：管理用户所属部门
      3.区域信息提取：根据部门获取区域信息

## 7. 智能体集成

###    实现服务

      MaxKBService：智能体服务
         实现与MaxKB智能体的交互
         支持会话管理
         提供智能对话功能

###    核心功能

      智能体对话：与MaxKB智能体进行交互
      会话管理：创建和维护会话ID
      文件分析：支持上传文件进行智能分析

#    API文档

## 1. 企业微信API

###    登录接口

      POST /qywechat/login

### 请求参数：

      json
      Apply
      {
         "code": "<企业微信授权code>"
      }

### 响应示例：

      json
      Apply
      {
         "code": 200,
         "message": "登录成功",
         "data": {
         "userId": "<用户ID>",
         "sessionKey": "<会话密钥>",
         "departmentId": "<部门ID>",
         "departmentName": "<部门名称>"
         }
      }

### 检查Session有效性

      POST /qywechat/checkSession

### 请求参数：


      json
      Apply
      {
         "sessionKey": "<会话密钥>"
      }

## 2. 报销API

###    提交报销申请

      POST /reimbursement/submit

### 请求参数：

      json
      Apply
      {
         "invoices": [
            {
            "invoiceNumber": "<发票号码>",
            "invoiceDate": "<开票日期>",
            "totalAmount": "<总金额>",
            "invoiceType": "<发票类型>"
            }
         ],
         "totalAmount": "<总金额>",
         "mediaIds": "<文件媒体ID>",
         "formType": "<表单类型>",
         "formReimbursementReason": "<报销事由>",
         "legalEntity": "<法人实体>",
         "region": "<区域>",
         "costDepartment": "<成本部门>"
      }

### 健康检查

访问以下URL检查系统健康状态：

      GET /qywechat/health

# 开发说明

## 代码规范

      1.遵循Spring Boot最佳实践
      2.使用Lombok简化代码
      3.使用Slf4j进行日志记录

### 测试

      mvn test

### 打包

      mvn clean package -DskipTests

# 注意事项

      1.企业微信配置需要正确填写，否则无法正常使用企业微信功能；
      2.发票验证功能需要配置有效的阿里云API AppCode；
      3.文件上传功能需要配置正确的OSS地址和AccessKey/SecretKey；
      4.智能体功能需要配置正确的MaxKB API地址和密钥。
