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

# 三、使用方式
## 企业微信  
1.参考企业微信官方文档，在企业微信中创建企业、自建应用，获取企业ID、自建应用ID、自建应用密钥等  
2.参考[基于AI+企微的智能报销系统](https://blog.csdn.net/m0_59880555/article/details/156271790?fromshare=blogdetail&sharetype=blogdetail&sharerId=156271790&sharerefer=PC&sharesource=m0_59880555&sharefrom=from_link)中四个审批模板，在“审批”中创建并修改审批模板，同时获取审批模板对应审批模板ID  
3.修改应用主页为前端运行后的主页  
4.若要使用企业微信登录功能，需要在自建应用后台配置“网页授权及JS-SDK”、“企业微信授权登录”、“企业可信IP”等  
## MaxKB智能体
1.获得智能体应用文件  
2.在部署好的MaxKB环境中导入智能体  
3.在智能体工作流“基本信息”中修改两个默认值  
4.模型节点的模型改为可用的模型  
5.（可选）模型提示词中示例部分可填入实际发票信息提高模型识别准确率
## 前端
1.获得前端源代码文件  
2.参考[前端详细说明](https://github.com/fit2cloud-edu/AI-Reimburse/blob/11b43e1c4e0253dc6decde936cf8f49b59993eda/frontend/README.md)修改配置文件  
3.在合适的地方运行前端
## 后端
1.获得后端源代码文件  
2.参考[后端详细说明](https://github.com/fit2cloud-edu/AI-Reimburse/blob/11b43e1c4e0253dc6decde936cf8f49b59993eda/backend/README.md)修改配置文件  
3.在合适的地方运行后端
## 阿里云发票真伪查验工具
1.使用该工具:[发票真伪查验工具](https://market.aliyun.com/detail/cmapi025075?spm=5176.29867242_210807074.0.0.44e83e7ekmJe2L#sku=yuncode1907500008)  
2.购买后获得AppCode填写在后端配置文件中
​
# 四、技术栈
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

# 五、参考资料
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
