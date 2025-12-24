import request from '@/utils/request'
import type { ApiResponse, ReimbursementSubmitData } from '@/api'

// 提交报销申请
export const submitReimbursement = async (
  data: ReimbursementSubmitData
): Promise<ApiResponse<string>> => {
  return request.post('/reimbursement/submit', data, {
    params: {
      userId: data.userId,
      userName: data.userName
    }
  })
}

// 获取报销记录
export const getReimbursementList = async (params?: {
  userId?: string
  status?: string
  page?: number
  size?: number
}): Promise<ApiResponse<any>> => {
  return request.get('/reimbursement/list', { params })
}

// 获取报销详情
export const getReimbursementDetail = async (id: string): Promise<ApiResponse<any>> => {
  return request.get(`/reimbursement/detail/${id}`)
}