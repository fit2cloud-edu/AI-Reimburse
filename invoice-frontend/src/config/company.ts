/**
 * 公司相关配置信息
 * 统一管理所有公司相关的敏感信息
 */

export interface CompanyConfig {
  // 企业微信配置
  wechatWork: {
    corpId: string;        // 企业CorpID
    agentId: string;       // 应用AgentID
    redirectUri: string;   // 回调地址
    state: string;         // 防CSRF攻击状态参数
  };
  
  // 域名配置
  domains: {
    production: string;    // 生产环境域名
    development: string;   // 开发环境域名
  };
  
}

// 公司配置信息
export const companyConfig: CompanyConfig = {
  wechatWork: {
    corpId: '',
    agentId: '',
    redirectUri: '',
    state: ''
  },
  
  domains: {
    production: '',
    development: ''
  }
  
};

// 环境判断
export const isProduction = window.location.hostname === companyConfig.domains.production;

// 获取当前环境域名
export const getCurrentDomain = () => {
  return isProduction ? companyConfig.domains.production : companyConfig.domains.development;
};

// 导出默认配置
export default companyConfig;