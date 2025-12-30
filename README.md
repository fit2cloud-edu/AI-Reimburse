<h1 align="center">基于 AI+企业微信的智能报销系统</h1>

</p>​

# 一、总体架构
基于 AI+企业微信的智能报销系统，主要结合当前主流的智能体，对接企业微信的报销流程，改进企业内部报销流程，提升报销效率。主要包含三部分内容，详细下图所示：

<div align="left"><img  src="https://github.com/user-attachments/assets/57dd672b-467a-4091-bc4d-5c94c2950b15" width = 600 /></div>
​图1：系统架构图

基于企业微信的自定义报销流程：可在企业微信企业管理后台完成报销流程定义以及报销模板发布，其中包含报销时需要提交的表单内容以及发票相关信息等；
基于 MaxKB 的发票智能识别 Agent：通过 AI 完成发票内容识别以及提交时发票文件处理。
报销入口应用：入口应用主要将发票批量传递给 Agent，Agent 则将识别归类好的发票传递给应用，用户在应用页面上进行确认修改，无误后直接提交，提交时应用调用企业微信的报销流程接口，完成报销发起/提交。

# 二、业务场景
AI 智能报销系统主要解决以下两种报销类型：

日常报销：员工日常的餐饮、团建、交通以及其他发票的报销，对应员工的日常报销申请流程；
差旅报销：员工非Base城市的差旅费用报销，企业内部包含出差申请、差旅补贴申请以及差旅费用报销申请等三个流程可一键提交报销，默认同时提交三个审批，差旅补贴申请是否提交可自选。
​
# 三、MaxKB工具链接
https://east-mk.fit2cloud.cn/chat/c3bc4670a201567a?corpid=ww71db56bf9d6bec37&corpsecret=jMv3sSf1naOLJuY07F3sxVViOXhO1Fsqzb8IZL5NvkQ&media_ids=

# 四、系统详细功能介绍参考：
https://blog.csdn.net/m0_59880555/article/details/156271790?fromshare=blogdetail&sharetype=blogdetail&sharerId=156271790&sharerefer=PC&sharesource=m0_59880555&sharefrom=from_link
