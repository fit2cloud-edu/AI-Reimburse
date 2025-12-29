import request from '@/utils/request'
import type { ApiResponse } from '@/api'

export interface LoginRequest {
  code: string
}

export interface LoginResponse {
  userId: string
  userName: string
  sessionKey: string
  expiresIn?: number
    departmentStructure?: {
    departmentId: string
    departmentName: string
    fullPath: string
    departmentHierarchy: string[]
    region: string
    regionDepartmentId: string
  }
  departmentId?: string
  departmentName?: string
  departmentFullPath?: string
  departmentHierarchy?: string[]
  region?: string
  regionDepartmentId?: string
}

export interface CheckSessionRequest {
  sessionKey: string
}

// 企业微信登录
export const login = async (code: string): Promise<ApiResponse<LoginResponse>> => {
  return request.post('/qywechat/web/login', { code })
}

// 检查session
export const checkSession = async (sessionKey: string): Promise<ApiResponse<boolean>> => {
  return request.post('/qywechat/web/checkSession', { sessionKey })
}

// 健康检查
export const healthCheck = async (): Promise<ApiResponse<string>> => {
  return request.get('/qywechat/web/health')
}