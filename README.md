<h1 align="center">基于 AI+企业微信的智能报销系统</h1>

</p>​

# 一、总体架构
基于 AI+企业微信的智能报销系统，主要结合当前主流的智能体，对接企业微信的报销流程，改进企业内部报销流程，提升报销效率。主要包含三部分内容，系统架构如下图所示：

<div align="center"><img  src="https://github.com/user-attachments/assets/57dd672b-467a-4091-bc4d-5c94c2950b15" width = 600 /><br/><strong>图1：系统架构图</strong></div>

基于企业微信的自定义报销流程：可在企业微信企业管理后台完成报销流程定义以及报销模板发布，其中包含报销时需要提交的表单内容以及发票相关信息等；  
基于 MaxKB 的发票智能识别 Agent：通过 AI 完成发票内容识别以及提交时发票文件处理。  
报销入口应用：入口应用主要将发票批量传递给 Agent，Agent 则将识别归类好的发票传递给应用，用户在应用页面上进行确认修改，无误后直接提交，提交时应用调用企业微信的报销流程接口，完成报销发起/提交。

# 二、业务场景
AI 智能报销系统主要解决以下两种报销类型：  
日常报销：员工日常的餐饮、团建、交通以及其他发票的报销，对应员工的日常报销申请流程；  
差旅报销：员工非Base城市的差旅费用报销，企业内部包含出差申请、差旅补贴申请以及差旅费用报销申请等三个流程可一键提交报销，默认同时提交三个审批，差旅补贴申请是否提交可自选。
​
# 三、技术栈
## 前端
- **前端框架**: Vue 3.5.25 + TypeScript
- **构建工具**: Vite 7.2.4
- **UI组件库**: Element Plus 2.12.0
- **状态管理**: Pinia 3.0.4
- **路由管理**: Vue Router 4.6.4
- **HTTP客户端**: Axios 1.13.2
- **样式预处理器**: Sass 1.96.0
- **代码规范**: ESLint + Prettier
- **测试框架**: Vitest + Playwright

## 后端
- **框架**：Spring Boot 3.4.11
- **开发语言**：Java 17
- **构建工具**：Maven
- **企业微信集成**：支持企业微信登录、审批流程
- **文件处理**：支持上传PDF、图片等发票文件
- **其他依赖**：Lombok、Spring Boot Actuato等

# 四、参考资料
1.开源企业级智能体平台MaxKB：  
[MaxKB开源项目地址](https://github.com/1panel-dev/MaxKB)  
[MaxKB使用手册](https://maxkb.cn/docs/v2/)  
2.系统详细功能介绍参考：  
[基于AI+企微的智能报销系统](https://blog.csdn.net/m0_59880555/article/details/156271790?fromshare=blogdetail&sharetype=blogdetail&sharerId=156271790&sharerefer=PC&sharesource=m0_59880555&sharefrom=from_link)  
3.系统前后端详细介绍和使用说明：  
[前端详细说明](https://github.com/fit2cloud-edu/AI-Reimburse/blob/11b43e1c4e0253dc6decde936cf8f49b59993eda/frontend/README.md)  
[后端详细说明](https://github.com/fit2cloud-edu/AI-Reimburse/blob/11b43e1c4e0253dc6decde936cf8f49b59993eda/backend/README.md)  
4.企业微信相关配置可参考官方文档:  
[企业微信官方开发者文档](https://developer.work.weixin.qq.com/)
