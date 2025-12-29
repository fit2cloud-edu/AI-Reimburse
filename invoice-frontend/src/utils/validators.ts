import type { InvoiceInfo } from '@/store/reimbursement'

// 校验规则
export const validators = {
  // 检查发票是否有严重错误
  hasSevereValidationError(
    validation: any,
    invoice: InvoiceInfo
  ): boolean {
    if (!validation || validation.valid) return false
    
    const violations = validation.violations || []
    
    // 检查真伪验证失败
    const hasVerificationFailed = violations.some((v: any) => 
      v.field === 'verification_status' && 
      v.message?.includes('真伪验证失败')
    )
    
    // 检查购买方名称错误（公司标准）
    const hasBuyerNameError = violations.some((v: any) => 
      v.affectedField === '购买方名称' && 
      v.severity === 'ERROR' &&
      v.message?.includes('杭州飞致云信息科技有限公司')
    )
    
    // 检查购买方税号错误（公司标准）
    const hasBuyerCodeError = violations.some((v: any) => 
      v.affectedField === '购买方代码' && 
      v.severity === 'ERROR' &&
      v.message?.includes('91330106311245339J')
    )
    
    // 检查个人购买方不属于公司
    const hasPersonalBuyerError = violations.some((v: any) => 
      v.affectedField === '购买方名称' && 
      v.severity === 'ERROR' &&
      v.message?.includes('不属于公司')
    )
    
    return hasVerificationFailed || hasBuyerNameError || hasBuyerCodeError || hasPersonalBuyerError
  },
  
  // 检查是否为特殊发票（火车票、飞机票等）
  isSpecialInvoice(validation: any, invoice: InvoiceInfo): boolean {
    if (!validation) return false
    
    const violations = validation.violations || []
    
    // 检查发票号码不正确的情况
    const hasInvalidInvoiceNo = violations.some((v: any) => 
      v.field === 'invoice_verification' && 
      v.message?.includes('发票号码不正确')
    )
    
    // 检查是否为8位数字的特殊票据
    const isSpecialTicket = invoice.invoiceNo && /^\d{8}$/.test(invoice.invoiceNo)
    
    // 检查是否有特殊票据标记
    const hasSpecialMark = violations.some((v: any) => 
      v.field === 'invoice_type' && 
      v.message?.includes('特殊票据')
    )
    
    return hasInvalidInvoiceNo || isSpecialTicket || hasSpecialMark
  },
  
  // 检查是否验证超限
  isVerificationLimitExceeded(validation: any): boolean {
    if (!validation || validation.valid) return false
    
    const violations = validation.violations || []
    
    return violations.some((v: any) => 
      v.field === 'invoice_verification' && 
      v.message?.includes('超过该张发票当日查验次数')
    )
  },
  
// 检查开票日期是否超过一年
  isInvoiceDateExpired(validation: any): boolean {
    if (!validation || validation.valid) return false
    
    const violations = validation.violations || []
    
    return violations.some((v: any) => 
      v.affectedField === '开票日期' && 
      v.severity === 'WARNING' &&
      v.message?.includes('超过1年')
    )
  },
  
  // 检查真伪验证状态
  getVerificationStatus(validation: any): 'SUCCESS' | 'FAILED' | 'SKIPPED' | 'ERROR' {
    if (!validation) return 'SKIPPED'
    
    const violations = validation.violations || []
    
    // 检查真伪验证失败
    const hasVerificationFailed = violations.some((v: any) => 
      v.field === 'verification_status' && 
      v.message?.includes('真伪验证失败')
    )
    
    // 检查验证跳过
    const hasVerificationSkipped = violations.some((v: any) => 
      v.field === 'verification_status' && 
      v.message?.includes('验证跳过')
    )
    
    // 检查验证异常
    const hasVerificationError = violations.some((v: any) => 
      v.field === 'verification_status' && 
      v.message?.includes('验证异常')
    )
    
    if (hasVerificationFailed) return 'FAILED'
    if (hasVerificationSkipped) return 'SKIPPED'
    if (hasVerificationError) return 'ERROR'
    
    return 'SUCCESS'
  },
  
  // 获取验证提示信息
  getValidationMessages(validation: any): string[] {
    if (!validation || validation.valid) return []
    
    const violations = validation.violations || []
    const messages: string[] = []
    
    // 检查真伪验证失败
    const verificationFailed = violations.find((v: any) => 
      v.field === 'verification_status' && 
      v.message?.includes('真伪验证失败')
    )
    if (verificationFailed) {
      messages.push('发票真伪验证失败，请检查发票真实性')
    }
    
    // 检查验证超限
    const limitExceeded = violations.find((v: any) => 
      v.field === 'invoice_verification' && 
      v.message?.includes('超过该张发票当日查验次数')
    )
    if (limitExceeded) {
      messages.push('该发票本日验证次数超过五次，无法验证，将在提交后人工验证')
    }
    
    // 检查开票日期超一年
    const dateExpired = violations.find((v: any) => 
      v.affectedField === '开票日期' && 
      v.severity === 'WARNING' &&
      v.message?.includes('超过1年')
    )
    if (dateExpired) {
      messages.push('开票日期不符合要求，请在下方"消费事由"中说明原因')
    }
    
    // 检查特殊票据
    const specialInvoice = violations.find((v: any) => 
      v.field === 'invoice_type' && 
      v.message?.includes('特殊票据')
    )
    if (specialInvoice) {
      messages.push('此为特殊票据（如飞机票、火车票等），系统无法自动验证真伪，需要提交后人工验证。')
    }
    
    return messages
  },

  // 获取字段校验状态
  getFieldValidation(
    validation: any,
    fieldName: string
  ): {
    severity: 'error' | 'warning' | ''
    message: string
  } {
    if (!validation || validation.valid) {
      return { severity: '', message: '' }
    }
    
    const fieldMapping: Record<string, string> = {
      'buyerName': '购买方名称',
      'buyerCode': '购买方代码',
      'invoiceDate': '开票日期',
      'reimbursementType': '费用类型',
      'totalAmount': '金额',
      'consumptionDate': '日期'
    }
    
    const backendField = fieldMapping[fieldName]
    if (!backendField) return { severity: '', message: '' }
    
    const violations = validation.violations || []
    const violation = violations.find((v: any) => v.affectedField === backendField)
    
    if (violation) {
      return {
        severity: violation.severity.toLowerCase() as 'error' | 'warning',
        message: violation.message
      }
    }
    
    return { severity: '', message: '' }
  },
  
  // 获取消费事由校验状态
  getConsumptionReasonValidation(
    validation: any,
    consumptionReason: string
  ): {
    required: boolean
    message: string
  } {
    if (!validation || validation.valid) {
      return { required: false, message: '' }
    }
    
    const violations = validation.violations || []
    const hasWarning = violations.some((v: any) => v.severity === 'WARNING')
    
    if (hasWarning && (!consumptionReason || consumptionReason.trim() === '')) {
      return {
        required: true,
        message: '请填写解释说明以继续提交'
      }
    }
    
    return { required: false, message: '' }
  },
  
  // 验证报销表单
  validateReimbursementForm(form: any, invoiceInfos: InvoiceInfo[]): string[] {
    const errors: string[] = []
    
    // 检查必填字段
    if (!form.legalEntity) errors.push('请选择法人实体')
    if (!form.reimbursementDate) errors.push('请选择报销日期')
    if (!form.region) errors.push('请选择区域')
    if (!form.costDepartment) errors.push('请选择费用承担部门')
    if (!form.formReimbursementReason?.trim()) errors.push('请填写报销事由')
    
    // 检查客成差旅字段
    if (form.formType === '客成差旅报销单') {
      if (!form.travelStartDate) errors.push('请选择出差开始日期')
      if (!form.travelEndDate) errors.push('请选择出差结束日期')
      
      if (form.travelStartDate && form.travelEndDate) {
        const startDate = new Date(form.travelStartDate)
        const endDate = new Date(form.travelEndDate)
        if (endDate < startDate) {
          errors.push('出差结束日期不能早于开始日期')
        }
      }
    }
    
    // 检查发票信息
    if (invoiceInfos.length === 0) {
      errors.push('请至少上传一张发票')
    } else {
      // 检查每张发票的必要字段
      invoiceInfos.forEach((invoice, index) => {
        if (!invoice.reimbursementType) {
          errors.push(`第 ${index + 1} 张发票缺少费用类型`)
        }
        if (!invoice.totalAmount) {
          errors.push(`第 ${index + 1} 张发票缺少金额`)
        }
      })
    }
    
    return errors
  }
}

export default validators