import request from '@/utils/request'
import type { AxiosResponse } from 'axios'
import axios from 'axios'

// 基础响应类型
export interface ApiResponse<T = any> {
  success: boolean
  data?: T
  message?: string
  code?: number
}

// 发票信息
export interface InvoiceInfo {
  buyerName?: string
  buyerCode?: string
  invoiceNo?: string
  invoiceDate?: string
  sellerName?: string
  totalAmount?: string
  reimbursementType?: string
  consumptionDate?: string
  remark?: string
  consumptionReason?: string
  mediaId?: string
}

// 发票验证结果
export interface ValidationResult {
  valid: boolean
  violations?: Array<{
    severity: 'ERROR' | 'WARNING'
    field: string
    message: string
    affectedField?: string
  }>
  fieldValidationMap?: Record<string, any>
  rawViolations?: any[]
}

// 文件上传响应
export interface FileUploadResponse {
  success: boolean
  data?: {
    invoiceInfos: InvoiceInfo[]
    mediaIds?: string
    validationResult?: {
      results: Array<{
        validationResult: ValidationResult
        invoiceIndex?: number
      }>
    }
  }
  message?: string
}

// 部门信息
export interface Department {
  id: number | string
  name: string
  parentId?: number | string
  region?: string
}

// 报销提交数据
export interface ReimbursementSubmitData {
  invoices: InvoiceInfo[]
  totalAmount: string
  mediaIds?: string
  formType: string
  formReimbursementReason: string
  legalEntity: string
  region: string
  costDepartment: string
  userId: string
  userName: string
  reimbursementDate: string
  customerName?: string
  unsignedCustomer?: string
  travelStartDate?: string
  travelStartPeriod?: string
  travelEndDate?: string
  travelEndPeriod?: string
  travelDays?: string
  submitTravelSubsidy?: boolean // 出差补贴申请单开关
}

// 重新导出所有API函数
export * from './auth'
export * from './department'
export * from './reimbursement'
export * from './upload'

// 重新导出类型
export * from './types'

export default request