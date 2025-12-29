import request from '@/utils/request'
import type { ApiResponse, FileUploadResponse } from '@/api'

// 批量上传文件
export const uploadInvoiceFiles = async (
  files: File[],
  formType: string,
  message: string = '发票'
): Promise<ApiResponse<FileUploadResponse['data']>> => {
  const formData = new FormData()
  files.forEach(file => {
    formData.append('files', file)
  })
  formData.append('formType', formType)
  formData.append('message', message)
  
  const response = await request.post('/upload/invoice', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    timeout: 300000 // 超时时间300秒（5分钟）
  })
  return response
}

// 单个文件上传（带session）
export const uploadSingleInvoice = async (
  file: File,
  sessionId: string,
  isLast: boolean,
  formType: string
): Promise<ApiResponse<any>> => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('sessionId', sessionId)
  formData.append('isLast', String(isLast))
  formData.append('formType', formType)
  
  const response = await request.post('/upload/invoice/single', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    timeout: 120000 // 超时时间120秒（2分钟）
  })
  return response
}

// 微盘文件上传
export const uploadWedriveFiles = async (
  sessionId: string,
  tickets: string[],
  message: string
): Promise<ApiResponse<any>> => {
  return request.post('/upload/wedrive', {
    sessionId,
    tickets,
    message
  })
}