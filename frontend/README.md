# 发票报销前端系统 (Fapiao Frontend)

一个基于 Vue 3 + TypeScript + Element Plus 的现代化发票报销前端应用，支持企业微信集成和文件上传功能。

## 技术栈

- **前端框架**: Vue 3.5.25 + TypeScript
- **构建工具**: Vite 7.2.4
- **UI组件库**: Element Plus 2.12.0
- **状态管理**: Pinia 3.0.4
- **路由管理**: Vue Router 4.6.4
- **HTTP客户端**: Axios 1.13.2
- **样式预处理器**: Sass 1.96.0
- **代码规范**: ESLint + Prettier
- **测试框架**: Vitest + Playwright

## 功能实现详细说明

### 1. 用户认证系统

#### 企业微信登录集成
- **环境检测**：自动检测是否在企业微信环境中运行
- **多种登录方式**：
  - 企业微信内嵌登录（使用JSSDK）
  - 新窗口扫码登录
  - 快速登录（在企业微信环境中）
- **路由守卫**：自动重定向未认证用户到登录页面
- **Token管理**：自动处理认证token的存储和刷新

#### 登录流程
1. 环境检测 → 2. 登录方式选择 → 3. 企业微信授权 → 4. 用户信息获取 → 5. 路由跳转

### 2. 发票报销流程

#### 多步骤表单设计
- **步骤1：选择报销类型**
  - 图形化类型选择界面
  - 支持两种报销表单类型（日常报销、差旅报销）
  - 实时表单验证

- **步骤2：上传发票文件**
  - 批量文件上传支持
  - 文件类型验证（图片、PDF、文档）
  - 文件大小限制控制
  - 上传进度提示

- **步骤3：填写报销信息**
  - 完整的报销信息表单
  - 部门信息选择
  - 法人实体选择
  - 日期选择器
  - 实时表单验证

#### 文件上传功能
- **FileUploader组件**：支持拖拽上传、点击上传
- **文件类型识别**：自动识别图片、文档等文件类型
- **上传进度监控**：实时显示上传进度和状态
- **错误处理**：网络错误、文件类型错误等异常处理

### 3. 状态管理与数据流

#### Pinia Store设计
- **Auth Store**：用户认证状态、用户信息、登录状态
- **Reimbursement Store**：报销表单数据、文件列表、流程状态

#### 数据流架构
用户操作 → Vue组件 → Pinia Actions → API调用 → 状态更新 → UI响应

### 4. 组件化架构

#### 核心组件
- **FlowSteps**：流程步骤指示器，支持步骤跳转
- **FileUploader**：文件上传组件，支持多种上传方式
- **InvoiceCard**：发票信息展示卡片
- **ValidationMarker**：表单验证标记组件
- **ProgressModal**：进度显示弹窗

### 5. API接口设计

#### 接口模块化
- **认证接口** (`auth.ts`)：登录、退出、用户信息
- **报销接口** (`reimbursement.ts`)：提交报销、获取记录
- **部门接口** (`department.ts`)：部门列表、部门信息
- **文件上传接口** (`upload.ts`)：文件上传、进度查询

#### 请求封装
- **统一错误处理**：网络错误、业务错误统一处理
- **请求拦截器**：自动添加认证token
- **响应拦截器**：统一处理响应数据和错误
- **超时配置**：合理的请求超时时间设置

## 项目结构
    src/ 
    ├── api/ # API接口定义
    │ ├── auth.ts # 认证相关接口 
    │ ├── department.ts # 部门相关接口 
    │ ├── reimbursement.ts # 报销相关接口 
    │ ├── upload.ts # 文件上传接口 
    │ └── types.ts # TypeScript类型定义 
    ├── assets/ # 静态资源 
    │ └── main.css # 全局样式 
    ├── components/ # 公共组件 
    │ ├── FileUploader.vue # 文件上传组件 
    │ ├── FlowSteps.vue # 流程步骤组件 
    │ ├── InvoiceCard.vue # 发票卡片组件 
    │ ├── ProgressModal.vue # 进度弹窗组件 
    │ └── ValidationMarker.vue # 验证标记组件 
    ├── config/ # 配置文件 
    │ └── company.ts # 企业配置信息 
    ├── router/ # 路由配置 
    │ └── index.ts # 路由定义 
    ├── store/ # 状态管理 
    │ ├── auth.ts # 认证状态管理 
    │ ├── reimbursement.ts # 报销状态管理 
    │ └── index.ts # Store入口 
    ├── utils/ # 工具函数 
    │ ├── fileUtils.ts # 文件处理工具 
    │ ├── request.ts # HTTP请求封装 
    │ ├── validators.ts # 表单验证工具 
    │ └── wxCompat.ts # 微信兼容工具 
    ├── views/ # 页面组件 
    │ ├── Login.vue # 登录页面 
    │ └── Reimbursement.vue # 报销页面 
    ├── App.vue # 根组件 
    └── main.ts # 应用入口


## 功能特性

### 核心功能
- ✅ **企业微信集成登录**：支持内嵌登录、扫码登录、快速登录
- ✅ **多步骤报销流程**：类型选择 → 文件上传 → 信息填写
- ✅ **批量文件上传**：支持图片、PDF、文档等多种格式
- ✅ **智能表单验证**：实时验证、错误提示、提交控制
- ✅ **部门信息管理**：动态加载部门列表，支持部门选择
- ✅ **用户状态管理**：登录状态持久化，自动路由跳转

### 技术特性
- 🔥 基于Vue 3 Composition API
- 📱 响应式设计，支持移动端打开
- 🎨 Element Plus UI组件库
- 🔒 TypeScript类型安全
- 🚀 Vite快速构建

## 环境要求

- Node.js: ^20.19.0 || >=22.12.0
- npm: 9.x 或更高版本

## 快速开始

### 1. 安装依赖
```bash
npm install
```

### 2. 环境配置
在环境配置文件中设置相应参数：

```bash
# 开发环境
cp .env.development .env.local

# 生产环境  
cp .env.production .env.local
```

### 3. 启动开发服务器
```bash
npm run dev
```
应用将在 http://localhost:3000 启动

### 4. 构建生产版本
```bash
npm run build
```

### 5. 预览生产构建
```bash
npm run preview
```

## 开发脚本

```bash
# 开发模式
npm run dev

# 构建生产版本
npm run build

# 类型检查
npm run type-check

# 代码格式化
npm run format

# 代码检查与修复
npm run lint

# 单元测试
npm run test:unit

# E2E测试
npm run test:e2e
```

## 配置说明

### Vite配置
项目使用Vite作为构建工具，主要配置包括：
- 路径别名：`@` 指向 `src` 目录
- 开发服务器代理：将 `/api` 请求代理到后端服务
- 自动导入：Element Plus组件自动导入

### 企业配置
企业相关配置位于 `src/config/company.ts`，包括：
- 企业ID配置
- 应用配置信息
- API基础路径

### API配置
API请求配置位于 `src/utils/request.ts`，包含：
- 请求拦截器（添加认证token）
- 响应拦截器（统一错误处理）
- 超时配置

## 部署说明

### 开发环境部署
1. 配置开发环境变量
2. 运行 `npm run build`
3. Login.vue中“测试登录”入口在取消注释后可直接使用

### 生产环境部署
1. 配置生产环境变量
2. 运行 `npm run build`
3. 配置Nginx反向代理或vite.config.ts文件中填写后端服务运行地址
4. 可以部署到服务器

### Nginx配置示例
```nginx
server {
    listen 80;
    server_name your-domain.com;
    
    location / {
        root /path/to/dist;
        index index.html;
        try_files $uri $uri/ /index.html;
    }
    
    location /api {
        proxy_pass http://your-backend-server/api;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 开发指南

### 添加新页面
1. 在 `src/views` 目录下创建Vue组件
2. 在 `src/router/index.ts` 中添加路由配置
3. 如需API调用，在 `src/api` 目录下添加接口定义

### 添加新组件
1. 在 `src/components` 目录下创建Vue组件
2. 组件支持自动导入，无需手动引入

### API开发规范
- 所有API请求使用 `src/utils/request.ts` 封装的axios实例
- 接口定义统一放在 `src/api` 目录
- TypeScript类型定义放在 `src/api/types.ts`

### 状态管理
- 使用Pinia进行状态管理
- Store模块化组织，按功能划分
- 状态变更通过actions进行

## 测试

### 单元测试
```bash
npm run test:unit
```

### E2E测试
```bash
npm run test:e2e
```

## 常见问题

### Q: 开发时如何配置代理？
A: 在 `vite.config.ts` 的 `server.proxy` 中配置后端API地址。

### Q: 如何添加新的Element Plus组件？
A: 项目已配置自动导入，直接在模板中使用即可。

### Q: 如何修改企业配置？
A: 修改 `src/config/company.ts` 中的配置项。

### Q: 文件上传大小限制如何调整？
A: 在 `src/components/FileUploader.vue` 中修改文件大小限制配置。
