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

// 从报销理由中提取出差日期
export function extractTravelDatesFromReason(reason: string): { startDate: string | null; endDate: string | null } {
  if (!reason || !reason.trim()) {
    return { startDate: null, endDate: null }
  }

  // 支持多种日期格式的正则表达式
  const datePatterns = [
    // 格式：2024-01-15 到 2024-01-20
    /(\d{4}-\d{1,2}-\d{1,2})\s*(?:到|至|-|~)\s*(\d{4}-\d{1,2}-\d{1,2})/,
    // 格式：2024/01/15 到 2024/01/20
    /(\d{4}\/\d{1,2}\/\d{1,2})\s*(?:到|至|-|~)\s*(\d{4}\/\d{1,2}\/\d{1,2})/,
    // 格式：2024年1月15日 到 2024年1月20日
    /(\d{4})年\s*(\d{1,2})月\s*(\d{1,2})日\s*(?:到|至|-|~)\s*(\d{4})年\s*(\d{1,2})月\s*(\d{1,2})日/,
    // 格式：1月15日 到 1月20日（自动补充当前年份）
    /(\d{1,2})月\s*(\d{1,2})日\s*(?:到|至|-|~)\s*(\d{1,2})月\s*(\d{1,2})日/,
    // 格式：1月15日上午 到 1月20日上午（包含时段信息）
    /(\d{1,2})月\s*(\d{1,2})日\s*(?:上午|下午)?\s*(?:到|至|-|~)\s*(\d{1,2})月\s*(\d{1,2})日\s*(?:上午|下午)?/,
    // 格式：3.4-3.24（月份.日期-月份.日期，自动补充当前年份）- 优先级提高
    /(\d{1,2})\.(\d{1,2})\s*(?:到|至|-|~)\s*(\d{1,2})\.(\d{1,2})/,
    // 格式：25.11.1-12.1（年份.月份.日期-月份.日期）- 添加年份验证
    /(\d{2,4})\.(\d{1,2})\.(\d{1,2})\s*(?:到|至|-|~)\s*(\d{1,2})\.(\d{1,2})/,
    // 格式：15日 到 20日（自动补充当前年份和月份）
    /(\d{1,2})日\s*(?:到|至|-|~)\s*(\d{1,2})日/,
    // 格式：1-2（日期-日期，自动补充当前年份和月份）
    /(\d{1,2})\s*(?:到|至|-|~)\s*(\d{1,2})/
  ]

  for (const pattern of datePatterns) {
    const match = reason.match(pattern)
    if (match) {
      let startDate: string | null = null
      let endDate: string | null = null

      if (pattern === datePatterns[0]) {
        // 格式：2024-01-15 到 2024-01-20
        if (match[1] && match[2]) {
          startDate = match[1]
          endDate = match[2]
        }
      } else if (pattern === datePatterns[1]) {
        // 格式：2024/01/15 到 2024/01/20
        if (match[1] && match[2]) {
          startDate = match[1].replace(/\//g, '-')
          endDate = match[2].replace(/\//g, '-')
        }
      } else if (pattern === datePatterns[2]) {
        // 格式：2024年1月15日 到 2024年1月20日
        if (match[1] && match[2] && match[3] && match[4] && match[5] && match[6]) {
          const startYear = match[1]
          const startMonth = match[2].padStart(2, '0')
          const startDay = match[3].padStart(2, '0')
          const endYear = match[4]
          const endMonth = match[5].padStart(2, '0')
          const endDay = match[6].padStart(2, '0')
          startDate = `${startYear}-${startMonth}-${startDay}`
          endDate = `${endYear}-${endMonth}-${endDay}`
        }
      } else if (pattern === datePatterns[3]) {
        // 格式：1月15日 到 1月20日
        if (match[1] && match[2] && match[3] && match[4]) {
          const currentYear = new Date().getFullYear()
          const startMonth = match[1].padStart(2, '0')
          const startDay = match[2].padStart(2, '0')
          const endMonth = match[3].padStart(2, '0')
          const endDay = match[4].padStart(2, '0')
          startDate = `${currentYear}-${startMonth}-${startDay}`
          endDate = `${currentYear}-${endMonth}-${endDay}`
        }
      } else if (pattern === datePatterns[4]) {
        // 格式：1月15日上午 到 1月20日上午（包含时段信息）
        if (match[1] && match[2] && match[3] && match[4]) {
          const currentYear = new Date().getFullYear()
          const startMonth = match[1].padStart(2, '0')
          const startDay = match[2].padStart(2, '0')
          const endMonth = match[3].padStart(2, '0')
          const endDay = match[4].padStart(2, '0')
          startDate = `${currentYear}-${startMonth}-${startDay}`
          endDate = `${currentYear}-${endMonth}-${endDay}`
        }
      } else if (pattern === datePatterns[5]) {
        // 格式：3.4-3.24（月份.日期-月份.日期，自动补充当前年份）
        if (match[1] && match[2] && match[3] && match[4]) {
          const currentDate = new Date()
          const currentYear = currentDate.getFullYear()
          const currentMonth = currentDate.getMonth() + 1
          
          const startMonth = match[1].padStart(2, '0')
          const startDay = match[2].padStart(2, '0')
          const endMonth = match[3].padStart(2, '0')
          const endDay = match[4].padStart(2, '0')
          
          // 验证月份有效性（1-12）
          const startMonthNum = parseInt(startMonth)
          const endMonthNum = parseInt(endMonth)
          if (startMonthNum >= 1 && startMonthNum <= 12 && endMonthNum >= 1 && endMonthNum <= 12) {
            // 智能判断年份：如果提取的月份大于当前月份，说明是去年的日期
            let targetYear = currentYear
            if (startMonthNum > currentMonth) {
              targetYear = currentYear - 1
            }
            
            startDate = `${targetYear}-${startMonth}-${startDay}`
            endDate = `${targetYear}-${endMonth}-${endDay}`
          }
        }
      } else if (pattern === datePatterns[6]) {
        // 格式：25.11.1-12.1（年份.月份.日期-月份.日期）
        if (match[1] && match[2] && match[3] && match[4] && match[5]) {
          const startYear = match[1].length === 2 ? `20${match[1]}` : match[1] // 处理2位年份
          const startMonth = match[2].padStart(2, '0')
          const startDay = match[3].padStart(2, '0')
          const endMonth = match[4].padStart(2, '0')
          const endDay = match[5].padStart(2, '0')
          
          // 验证年份有效性（1900-当前年份+1）
          const startYearNum = parseInt(startYear)
          const currentYear = new Date().getFullYear()
          if (startYearNum >= 1900 && startYearNum <= currentYear + 1) {
            startDate = `${startYear}-${startMonth}-${startDay}`
            endDate = `${startYear}-${endMonth}-${endDay}`
          }
        }
      } else if (pattern === datePatterns[7]) {
        // 格式：15日 到 20日
        if (match[1] && match[2]) {
          const currentDate = new Date()
          const currentYear = currentDate.getFullYear()
          const currentMonth = currentDate.getMonth() + 1
          const currentDay = currentDate.getDate()
          
          const startDay = match[1].padStart(2, '0')
          const endDay = match[2].padStart(2, '0')
          
          // 智能判断年份和月份：如果提取的日期大于当前日期，说明是上个月的日期
          let targetYear = currentYear
          let targetMonth = currentMonth.toString().padStart(2, '0')
          
          const startDayNum = parseInt(startDay)
          if (startDayNum > currentDay) {
            // 如果开始日期大于当前日期，说明是上个月
            if (currentMonth === 1) {
              targetYear = currentYear - 1
              targetMonth = '12'
            } else {
              targetMonth = (currentMonth - 1).toString().padStart(2, '0')
            }
          }
          
          startDate = `${targetYear}-${targetMonth}-${startDay}`
          endDate = `${targetYear}-${targetMonth}-${endDay}`
        }
      } else if (pattern === datePatterns[8]) {
        // 格式：1-2（日期-日期，自动补充当前年份和月份）
        if (match[1] && match[2]) {
          const currentDate = new Date()
          const currentYear = currentDate.getFullYear()
          const currentMonth = currentDate.getMonth() + 1
          const currentDay = currentDate.getDate()
          
          const startDay = match[1].padStart(2, '0')
          const endDay = match[2].padStart(2, '0')
          
          // 智能判断年份和月份：如果提取的日期大于当前日期，说明是上个月的日期
          let targetYear = currentYear
          let targetMonth = currentMonth.toString().padStart(2, '0')
          
          const startDayNum = parseInt(startDay)
          if (startDayNum > currentDay) {
            // 如果开始日期大于当前日期，说明是上个月
            if (currentMonth === 1) {
              targetYear = currentYear - 1
              targetMonth = '12'
            } else {
              targetMonth = (currentMonth - 1).toString().padStart(2, '0')
            }
          }
          
          startDate = `${targetYear}-${targetMonth}-${startDay}`
          endDate = `${targetYear}-${targetMonth}-${endDay}`
        }
      }

      // 验证日期有效性并统一为上午到上午
      if (startDate && endDate && isValidDate(startDate) && isValidDate(endDate)) {
        // 统一为上午到上午，避免0.5天误差
        const adjustedStartDate = adjustToMorning(startDate)
        const adjustedEndDate = adjustToMorning(endDate)
        
        return { startDate: adjustedStartDate, endDate: adjustedEndDate }
      }
    }
  }

  return { startDate: null, endDate: null }
}

// 将日期调整为上午（避免0.5天误差）
// 将日期调整为上午（避免0.5天误差）
function adjustToMorning(dateString: string): string {
  const parts = dateString.split('-')
  if (parts.length === 3) {
    const year = parseInt(parts[0] || '0')
    const month = parseInt(parts[1] || '0') - 1
    const day = parseInt(parts[2] || '0')
    
    // 创建日期对象并设置为上午时间（00:00:00）
    const date = new Date(year, month, day, 0, 0, 0, 0)
    
    // 格式化为 YYYY-MM-DD 格式
    const formattedYear = date.getFullYear()
    const formattedMonth = (date.getMonth() + 1).toString().padStart(2, '0')
    const formattedDay = date.getDate().toString().padStart(2, '0')
    
    return `${formattedYear}-${formattedMonth}-${formattedDay}`
  }
  return dateString
}

// 验证日期有效性
function isValidDate(dateString: string): boolean {
  const parts = dateString.split('-')
  if (parts.length === 3) {
    const year = parseInt(parts[0] || '0')
    const month = parseInt(parts[1] || '0') - 1
    const day = parseInt(parts[2] || '0')
    
    // 使用本地时间创建日期对象
    const date = new Date(year, month, day)
    
    // 验证日期是否有效
    return !isNaN(date.getTime()) && 
           date.getFullYear() === year && 
           date.getMonth() === month && 
           date.getDate() === day
  }
  return false
}


export default validators

