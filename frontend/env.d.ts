/// <reference types="vite/client" />

// 企业微信JS-SDK类型声明
interface WxWork {
  invoke(
    method: string,
    params: any,
    callback: (res: { err_msg: string; [key: string]: any }) => void
  ): void;
  config?(config: any): void;
  ready?(callback: () => void): void;
  error?(callback: (error: any) => void): void;
  [key: string]: any;
}

// 使用declare global来扩展Window接口
declare global {
  interface Window {
    wx?: WxWork;
  }
  
  // 全局声明wx对象
  const wx: WxWork | undefined;
}

interface ImportMetaEnv {
  readonly VITE_APP_TITLE: string
  readonly VITE_APP_BASE_URL: string
  readonly VITE_APP_ENV: string
  readonly VITE_APP_VERSION: string
  readonly VITE_APP_DOMAIN: string
  readonly MODE: string
  readonly PROD: boolean
  readonly DEV: boolean
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

// 添加Node环境变量类型定义
declare namespace NodeJS {
  interface ProcessEnv {
    readonly NODE_ENV: 'development' | 'production' | 'test'
  }
}
