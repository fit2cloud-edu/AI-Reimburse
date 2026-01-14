import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'

export interface InvoiceInfo {
  id?: string
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
  subReimbursementType?: string
  index?: number
  duplicateCheckResult?: DuplicateCheckResult  // 查重结果字段
}

export interface DuplicateCheckResult {
  duplicate: boolean
  duplicateReason: string
  invoiceNumber: string
  invoiceDate: string
  userId: string
  checkStrategy: string
}

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

export const useReimbursementStore = defineStore('reimbursement', () => {
  // 流程状态
  const currentStep = ref(0)
  const formType = ref('')
  
  // 文件上传
  const selectedFiles = ref<File[]>([])
  const uploading = ref(false)
  const uploadStats = reactive({
    images: 0,
    documents: 0,
    total: 0
  })
  
  // 发票信息
  const invoiceInfos = ref<InvoiceInfo[]>([])
  const invoiceValidations = ref<Record<number, ValidationResult>>({})
  const activeInvoiceIndex = ref(-1)
  const totalAmount = ref('0.00')
  
  // 报销表单
  const reimbursementForm = reactive({
    formType: '',
    submitter: '',
    legalEntity: '杭州飞致云信息科技有限公司（CODE1）',
    reimbursementDate: '',
    region: '',
    costDepartment: '',
    formReimbursementReason: '',
    customerName: '',
    unsignedCustomer: '',
    travelStartDate: '',
    travelStartPeriod: '上午',
    travelEndDate: '',
    travelEndPeriod: '下午',
    travelDays: '0'
  })
  
  // 提交状态
  const submitting = ref(false)
  const submitProgress = reactive({
    currentStep: 0,
    totalSteps: 0,
    message: '',
    steps: [] as Array<{ name: string; completed: boolean }>
  })
  
  // 计算总金额
  const calculateTotalAmount = () => {
    let total = 0
    invoiceInfos.value.forEach(invoice => {
      const amountStr = invoice.totalAmount || '0'
      const amount = parseFloat(amountStr.replace('元', '') || '0')
      total += isNaN(amount) ? 0 : amount
    })
    totalAmount.value = total.toFixed(2)
    return totalAmount.value
  }
  
  // 更新文件统计
  const updateFileStats = (files: File[]) => {
    let images = 0
    let documents = 0
    
    files.forEach(file => {
      const parts = file.name.split('.')
      const extension = parts.length > 1 ? parts.pop()?.toLowerCase() : undefined
      if (extension && ['jpg', 'jpeg', 'png'].includes(extension)) {
        images++
      } else if (extension && ['pdf'].includes(extension)) {
        documents++
      }
    })
    
    uploadStats.images = images
    uploadStats.documents = documents
    uploadStats.total = files.length
  }
  
  // 添加发票
  const addInvoices = (invoices: InvoiceInfo[], validations?: Record<number, ValidationResult>) => {
    const startIndex = invoiceInfos.value.length
    invoices.forEach((invoice, index) => {
      const newIndex = startIndex + index
      invoice.index = newIndex
      invoiceInfos.value.push(invoice)
      
      if (validations && validations[index]) {
        invoiceValidations.value[newIndex] = validations[index]
      }
    })
    calculateTotalAmount()
  }
  
  // 删除发票
  const deleteInvoice = (index: number) => {
    if (index >= 0 && index < invoiceInfos.value.length) {
      invoiceInfos.value.splice(index, 1)
      delete invoiceValidations.value[index]
      
      // 重新索引
      const newValidations: Record<number, ValidationResult> = {}
      Object.keys(invoiceValidations.value).forEach(key => {
        const oldIndex = parseInt(key)
        if (oldIndex > index) {
            const validation = invoiceValidations.value[oldIndex]
            if (validation !== undefined) {
            newValidations[oldIndex - 1] = validation
            }
        } else if (oldIndex < index) {
            const validation = invoiceValidations.value[oldIndex]
            if (validation !== undefined) {
            newValidations[oldIndex] = validation
            }
        }
      })
      invoiceValidations.value = newValidations
      
      calculateTotalAmount()
    }
  }
  
  // 重置状态
  const reset = () => {
    currentStep.value = 0
    formType.value = ''
    selectedFiles.value = []
    invoiceInfos.value = []
    invoiceValidations.value = {}
    activeInvoiceIndex.value = -1
    totalAmount.value = '0.00'
    uploading.value = false
    submitting.value = false
    
    Object.assign(reimbursementForm, {
      submitter: '',
      legalEntity: '杭州飞致云信息科技有限公司（CODE1）',
      reimbursementDate: new Date().toISOString().split('T')[0],
      region: '',
      costDepartment: '',
      formReimbursementReason: '',
      customerName: '',
      unsignedCustomer: '',
      travelStartDate: '',
      travelStartPeriod: '上午',
      travelEndDate: '',
      travelEndPeriod: '下午',
      travelDays: '0'
    })
    
    Object.assign(uploadStats, {
      images: 0,
      documents: 0,
      total: 0
    })
    
    Object.assign(submitProgress, {
      currentStep: 0,
      totalSteps: 0,
      message: '',
      steps: []
    })
  }
  
    // 初始化
    const today = new Date().toISOString().split('T')[0] || new Date().toISOString().split('T')[0]
    reimbursementForm.reimbursementDate = today || ''
  
  return {
    // 状态
    currentStep,
    formType,
    selectedFiles,
    uploading,
    uploadStats,
    invoiceInfos,
    invoiceValidations,
    activeInvoiceIndex,
    totalAmount,
    reimbursementForm,
    submitting,
    submitProgress,
    
    // 方法
    calculateTotalAmount,
    updateFileStats,
    addInvoices,
    deleteInvoice,
    reset
  }
})