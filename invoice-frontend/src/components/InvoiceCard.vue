<template>
  <div class="invoice-card" :class="{ 'active': isActive, 'has-error': hasError, 'has-warning': hasWarning }">
    <!-- 发票标题栏 -->
    <div class="invoice-header" @click="handleToggle">
      <div class="header-left">
        <div class="invoice-index">
          <span class="index-number">{{ index + 1 }}</span>
        </div>
        <div class="invoice-title">
          <span class="title-text">发票 {{ index + 1 }}</span>
          <ValidationMarker 
            v-if="validation" 
            :validation="validation"
            size="small"
          />
          <span v-if="invoice.totalAmount" class="amount-badge">
            {{ invoice.totalAmount }}
          </span>
        </div>
      </div>
      <div class="header-right">
        <el-button 
          v-if="showDelete" 
          type="danger" 
          size="small" 
          @click.stop="handleDelete"
        >
          删除
        </el-button>
        <el-icon :class="['toggle-icon', { 'expanded': isActive }]">
          <ArrowDown />
        </el-icon>
      </div>
    </div>
    
    <!-- 发票详情 -->
    <el-collapse-transition>
      <div v-show="isActive" class="invoice-content">
        <!-- 识别信息 -->
        <div class="invoice-info">
          <div class="section-title">
            <el-icon><Document /></el-icon>
            <span>识别信息</span>
          </div>
          
          <div class="info-grid">
            <div class="info-item" v-for="field in basicFields" :key="field.key">
              <div class="info-label">{{ field.label }}</div>
              <div class="info-value" :class="getValidationClass(field.key)">
                {{ getFieldValue(field.key) || '未识别' }}
              </div>
              <!-- 购买方名称特殊处理：显示是否为企业员工 -->
              <div v-if="field.key === 'buyerName' && props.invoice.buyerName && isPersonalInvoice" 
                   class="info-message" :class="getBuyerNameColor">
                {{ isEnterpriseEmployee ? '是企业员工' : '不是企业员工' }}
              </div>
              <!-- 其他字段的错误消息显示 -->
              <div v-else-if="getFieldMessage(field.key)" class="info-message">
                {{ getFieldMessage(field.key) }}
              </div>
            </div>
          </div>

        </div>
        
        <!-- 验证提示信息 -->
        <div v-if="showValidationMessages" class="validation-messages">
          <!-- 错误提示（红色框） -->
          <div v-if="hasError" class="validation-message error-message">
            <el-icon><Warning /></el-icon>
            <span>存在严重问题，禁止提交</span>
          </div>
          
          <!-- 警告提示（黄色框） -->
          <div v-if="hasWarning && (hasDateWarning || hasLimitWarning)" class="validation-message warning-message">
            <el-icon><InfoFilled /></el-icon>
            <div class="warning-content">
              <div v-if="hasDateWarning">开票日期超出报销时间规则限制，请在下方写清楚原由</div>
              <div v-if="hasLimitWarning">该发票已经超过今日真伪识别次数上限，请删去后改日提交或现在提交后人工核对</div>
            </div>
          </div>
          
          <!-- 特殊票据提示（蓝色框） -->
          <div v-if="isSpecial" class="validation-message special-message">
            <el-icon><InfoFilled /></el-icon>
            <span>此为特殊票据（如飞机票、火车票等），系统无法自动验证真伪，需要提交后人工验证。</span>
          </div>
        </div>

        <!-- 填写信息 -->
        <div class="invoice-form">
          <div class="section-title">
            <el-icon><Edit /></el-icon>
            <span>填写信息</span>
          </div>
          
          <div class="form-grid">
            <!-- 费用类型 -->
            <div class="form-item" :class="getValidationClass('reimbursementType')">
              <label class="form-label required">费用类型</label>
              <el-select
                v-model="localInvoice.reimbursementType"
                placeholder="请选择费用类型"
                size="small"
                @change="handleFieldChange('reimbursementType')"
              >
                <el-option
                  v-for="type in reimbursementTypes"
                  :key="type"
                  :label="type"
                  :value="type"
                />
              </el-select>
              <div v-if="getFieldMessage('reimbursementType')" class="form-message">
                {{ getFieldMessage('reimbursementType') }}
              </div>
            </div>
            
            <!-- 金额 -->
            <div class="form-item" :class="getValidationClass('totalAmount')">
              <label class="form-label required">金额</label>
              <el-input
                v-model="localInvoice.totalAmount"
                placeholder="请输入金额"
                size="small"
                @change="handleFieldChange('totalAmount')"
              >
                <template #append>元</template>
              </el-input>
              <div v-if="getFieldMessage('totalAmount')" class="form-message">
                {{ getFieldMessage('totalAmount') }}
              </div>
            </div>
            
            <!-- 日期 -->
            <div class="form-item" :class="getValidationClass('consumptionDate')">
              <label class="form-label">日期</label>
              <el-date-picker
                v-model="localInvoice.consumptionDate"
                type="date"
                placeholder="选择日期"
                size="small"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                @change="handleFieldChange('consumptionDate')"
              />
              <div v-if="getFieldMessage('consumptionDate')" class="form-message">
                {{ getFieldMessage('consumptionDate') }}
              </div>
            </div>
            
            <!-- 其他说明 -->
            <div class="form-item full-width">
              <label class="form-label">其他说明（选填）</label>
              <el-input
                v-model="localInvoice.remark"
                type="textarea"
                :rows="2"
                placeholder="请输入其他说明"
                size="small"
                maxlength="200"
                show-word-limit
                @change="handleFieldChange('remark')"
              />
            </div>
            
            <!-- 消费事由 -->
            <div class="form-item full-width" :class="{ 'warning-required': hasConsumptionReasonWarning }">
              <label class="form-label">
                消费事由（选填）
                <span v-if="hasConsumptionReasonWarning" class="required-tag">*</span>
              </label>
              <el-input
                v-model="localInvoice.consumptionReason"
                type="textarea"
                :rows="2"
                :placeholder="consumptionReasonPlaceholder"
                size="small"
                maxlength="500"
                show-word-limit
                @change="handleFieldChange('consumptionReason')"
              />
              <div v-if="hasConsumptionReasonWarning && !localInvoice.consumptionReason?.trim()" 
                   class="form-message warning">
                请填写解释说明以继续提交
              </div>
            </div>
          </div>
        </div>
        
        <!-- 校验信息
        <div v-if="showValidation && validation && !validation.valid" class="validation-info">
          <div class="section-title">
            <el-icon><Warning /></el-icon>
            <span>校验信息</span>
          </div>
          
          <div class="validation-items">
            <div 
              v-for="(violation, vIndex) in validation.violations" 
              :key="vIndex"
              class="validation-item"
              :class="violation.severity?.toLowerCase()"
            >
              <el-icon :class="violation.severity?.toLowerCase()">
                <component :is="getViolationIcon(violation.severity)" />
              </el-icon>
              <div class="validation-content">
                <div class="validation-field">
                  {{ getFieldLabel(violation.affectedField || violation.field) }}:
                </div>
                <div class="validation-message">{{ violation.message }}</div>
              </div>
            </div>
          </div>
        </div> -->

      </div>
    </el-collapse-transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import {
  ArrowDown,
  Document,
  Edit,
  Warning,
  CircleCheck,
  InfoFilled,
  Close
} from '@element-plus/icons-vue'
import validators from '../utils/validators'
import ValidationMarker from './ValidationMarker.vue'
import type { InvoiceInfo, ValidationResult } from '../store/reimbursement'

interface Props {
  invoice: InvoiceInfo
  index: number
  activeIndex: number
  validation?: ValidationResult
  reimbursementTypes?: string[]
  showDelete?: boolean
  showValidation?: boolean
}

interface Emits {
  (e: 'select', index: number): void
  (e: 'update', index: number, field: string, value: any): void
  (e: 'delete', index: number): void
}

const props = withDefaults(defineProps<Props>(), {
  reimbursementTypes: () => [
    '交通费', '福利费', '办公费', '快递费', '业务招待费',
    '礼品费', '技术服务费', '会议费', '培训费', '中介服务费',
    '推广费', 'POC费用', '咨询费', '中介机构费', '差旅成本'
  ],
  showDelete: true,
  showValidation: true
})

const emit = defineEmits<Emits>()

// 本地副本
const localInvoice = ref({ ...props.invoice })

// 基础字段配置
const basicFields = [
  { key: 'buyerName', label: '购买方名称' },
  { key: 'buyerCode', label: '购买方代码' },
  { key: 'invoiceNo', label: '发票号码' },
  { key: 'invoiceDate', label: '开票日期' },
  { key: 'sellerName', label: '销售方' },
  { key: 'totalAmount', label: '金额' }
]

// 计算属性
const isActive = computed(() => props.activeIndex === props.index)

// 判断是否为企业员工
const isEnterpriseEmployee = computed(() => {
  if (!props.invoice.buyerName) return false
  // 如果购买方名称是个人姓名，且不是公司名称，则认为是企业员工
  return !props.invoice.buyerName.includes('公司') && 
         !props.invoice.buyerName.includes('有限') && 
         !props.invoice.buyerName.includes('责任')
})

// 计算属性
const hasError = computed(() => {
  // 如果验证通过（valid: true），就不显示错误状态
  if (!props.validation || props.validation.valid) return false
  const violations = props.validation.violations || []
  
  return violations.some((v: any) => 
    v.severity === 'ERROR'
  )
})

const hasWarning = computed(() => {
  // 如果验证通过（valid: true），就不显示警告状态
  if (!props.validation || props.validation.valid) return false
  const violations = props.validation.violations || []
  
  return violations.some((v: any) => 
    v.severity === 'WARNING'
  )
})

const isSpecial = computed(() => {
  // 如果验证通过（valid: true），就不显示特殊票据状态
  if (!props.validation || props.validation.valid) return false
  const violations = props.validation.violations || []
  
  return violations.some((v: any) => 
    (v.field === 'invoice_verification' && v.message?.includes('发票号码不正确')) ||
    (v.field === 'invoice_type' && v.message?.includes('特殊票据')) ||
    (v.message?.includes('特殊票据'))
  )
})

const hasDateWarning = computed(() => {
  if (!props.validation) return false
  const violations = props.validation.violations || []
  
  return violations.some((v: any) => 
    v.affectedField === '开票日期' && 
    (v.severity === 'WARNING' || v.severity === 'ERROR') &&
    v.message?.includes('超过1年')
  )
})

const hasLimitWarning = computed(() => {
  // 如果验证通过（valid: true），就不显示次数限制警告
  if (!props.validation || props.validation.valid) return false
  const violations = props.validation.violations || []
  
  return violations.some((v: any) => 
    v.field === 'invoice_verification' && 
    v.message?.includes('超过该张发票当日查验次数')
  )
})

// 判断是否为个人发票（购买方名称不包含"公司"、"有限"、"责任"等企业关键词）
const isPersonalInvoice = computed(() => {
  const buyerName = props.invoice.buyerName || ''
  const companyKeywords = ['公司', '有限', '责任', '集团', '企业', '厂', '店', '中心']
  return !companyKeywords.some(keyword => buyerName.includes(keyword))
})

// 获取购买方名称的验证信息
const getBuyerNameValidation = computed(() => {
  if (!props.validation) return null
  
  const violations = props.validation.violations || []
  const buyerNameViolation = violations.find(v => 
    v.affectedField === '购买方名称' || v.field === 'buyerName'
  )
  
  return buyerNameViolation
})

// 获取购买方名称的验证消息
const getBuyerNameMessage = computed(() => {
  const validation = getBuyerNameValidation.value
  if (!validation) return ''
  
  return validation.message || ''
})

// 获取购买方名称的验证颜色
const getBuyerNameColor = computed(() => {
  // const validation = getBuyerNameValidation.value
  // if (!validation) return ''
  
  // 如果是企业员工，用蓝色；如果不是企业员工，用红色
  if (isEnterpriseEmployee.value) {
    return 'blue'
  } else {
    return 'red'
  }
})

const showValidationMessages = computed(() => {
  // 如果验证通过（valid: true），就不显示验证消息
  if (!props.validation || props.validation.valid) return false
  const violations = props.validation.violations || []
  return violations.length > 0
})

const hasConsumptionReasonWarning = computed(() => {
  // 如果验证通过（valid: true），就不显示消费事由警告
  if (!props.validation || props.validation.valid) return false
  
  const violations = props.validation.violations || []
  
  // 只检查与消费事由相关的警告
  const hasConsumptionWarning = violations.some((v: any) => 
    v.severity === 'WARNING' && 
    (v.field === 'consumptionReason' || v.message?.includes('消费事由') || v.message?.includes('解释说明'))
  )
  
  return hasConsumptionWarning && (!localInvoice.value.consumptionReason?.trim())
})

const consumptionReasonPlaceholder = computed(() => {
  if (hasConsumptionReasonWarning.value) {
    return '请填写解释说明以继续提交'
  }
  return '请输入消费事由'
})

// 监听外部变化
watch(() => props.invoice, (newInvoice) => {
  localInvoice.value = { ...newInvoice }
}, { deep: true })

// 获取字段值
const getFieldValue = (field: string) => {
  return localInvoice.value[field as keyof InvoiceInfo]
}

// 获取字段标签
const getFieldLabel = (fieldName?: string) => {
  if (!fieldName) return '未知字段'
  
  const fieldMap: Record<string, string> = {
    'buyerName': '购买方名称',
    'buyerCode': '购买方代码',
    'invoiceDate': '开票日期',
    'reimbursementType': '费用类型',
    'totalAmount': '金额',
    'consumptionDate': '日期',
    'invoice_verification': '发票验证',
    'compliance_check': '合规检查'
  }
  
  return fieldMap[fieldName] || fieldName
}

// 获取字段消息
const getFieldMessage = (fieldName: string) => {
  if (!props.validation) return ''
  
  const fieldValidation = validators.getFieldValidation(props.validation, fieldName)
  return fieldValidation ? fieldValidation.message : ''
}


// 获取校验信息
const getFieldValidation = (fieldName: string) => {
  if (!props.validation) return null
  
  return validators.getFieldValidation(props.validation, fieldName)
}

const getValidationClass = (fieldName: string) => {
  const validation = getFieldValidation(fieldName)
  if (!validation) return ''
  
  return validation.severity?.toLowerCase() || ''
}

// 获取违规图标
const getViolationIcon = (severity?: string) => {
  switch (severity?.toLowerCase()) {
    case 'error':
      return Warning
    case 'warning':
      return InfoFilled
    default:
      return InfoFilled
  }
}

// 处理展开/折叠
const handleToggle = () => {
  emit('select', props.index)
}

// 处理字段变更
const handleFieldChange = (field: string) => {
  emit('update', props.index, field, localInvoice.value[field as keyof InvoiceInfo])
}

// 处理删除
const handleDelete = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要删除这张发票吗？',
      '确认删除',
      {
        type: 'warning',
        confirmButtonText: '确定',
        cancelButtonText: '取消'
      }
    )
    emit('delete', props.index)
  } catch {
    // 用户取消删除
  }
}
</script>

<style scoped lang="scss">
.invoice-card {
  background: white;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  margin-bottom: 12px;
  transition: all 0.3s ease;
  
  &.active {
    border-color: #409eff;
    box-shadow: 0 2px 12px 0 rgba(64, 158, 255, 0.1);
  }
  
  &.has-error {
    border-color: #f56c6c;
  }
  
  &.has-warning {
    border-color: #e6a23c;
  }
  
  .invoice-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px;
    cursor: pointer;
    user-select: none;
    
    .header-left {
      display: flex;
      align-items: center;
      gap: 12px;
      
      .invoice-index {
        display: flex;
        align-items: center;
        gap: 6px;
        
        .index-number {
          width: 28px;
          height: 28px;
          background: #409eff;
          color: white;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          font-weight: bold;
          font-size: 14px;
        }
      }
      
      .invoice-title {
        display: flex;
        align-items: center;
        gap: 10px;
        
        .title-text {
          font-weight: 600;
          font-size: 16px;
          color: #303133;
        }
        
        .amount-badge {
          background: #f0f9eb;
          color: #67c23a;
          padding: 2px 8px;
          border-radius: 4px;
          font-size: 12px;
          font-weight: 500;
        }
      }
    }
    
    .header-right {
      display: flex;
      align-items: center;
      gap: 12px;
      
      .toggle-icon {
        transition: transform 0.3s ease;
        color: #909399;
        
        &.expanded {
          transform: rotate(180deg);
        }
      }
    }
  }
  
  .invoice-content {
    border-top: 1px solid #ebeef5;
    padding: 20px;
    
    .section-title {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 16px;
      color: #303133;
      font-weight: 600;
      font-size: 15px;
      
      .el-icon {
        color: #409eff;
      }
    }
    
    .info-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: 16px;
      margin-bottom: 24px;
      
      .info-item {
        .info-label {
          color: #909399;
          font-size: 12px;
          margin-bottom: 4px;
        }
        
        .info-value {
          color: #303133;
          font-size: 14px;
          word-break: break-word;
          
          &.error {
            color: #f56c6c;
            font-weight: bold;
          }
          
          &.warning {
            color: #e6a23c;
            font-weight: bold;
          }
        }
        
        .info-message {
          color: #f56c6c;
          font-size: 12px;
          margin-top: 2px;
          
          &.blue {
            color: #409eff;
          }
          
          &.red {
            color: #f56c6c;
          }
        }
      }
    }
    
    .form-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: 16px;
      margin-bottom: 24px;
      
      .form-item {
        &.error {
          .el-select,
          .el-input,
          .el-date-editor {
            :deep(.el-input__wrapper) {
              border-color: #f56c6c;
            }
          }
        }
        
        &.warning {
          .el-select,
          .el-input,
          .el-date-editor {
            :deep(.el-input__wrapper) {
              border-color: #e6a23c;
            }
          }
        }
        
        &.warning-required {
          .form-label {
            color: #e6a23c;
          }
        }
        
        &.full-width {
          grid-column: 1 / -1;
        }
        
        .form-label {
          display: block;
          color: #606266;
          font-size: 14px;
          margin-bottom: 8px;
          
          &.required::after {
            content: " *";
            color: #f56c6c;
          }
          
          .required-tag {
            color: #f56c6c;
            margin-left: 2px;
          }
        }
        
        .form-message {
          font-size: 12px;
          margin-top: 4px;
          
          &.error {
            color: #f56c6c;
          }
          
          &.warning {
            color: #e6a23c;
          }
        }
      }
    }
    
    .validation-info {
      border-top: 1px solid #ebeef5;
      padding-top: 16px;
      margin-top: 16px;
      
      .validation-items {
        .validation-item {
          display: flex;
          align-items: flex-start;
          gap: 8px;
          padding: 8px;
          border-radius: 4px;
          margin-bottom: 8px;
          
          &.error {
            background-color: #fef0f0;
            
            .el-icon {
              color: #f56c6c;
            }
          }
          
          &.warning {
            background-color: #fdf6ec;
            
            .el-icon {
              color: #e6a23c;
            }
          }
          
          .el-icon {
            margin-top: 2px;
            flex-shrink: 0;
          }
          
          .validation-content {
            flex: 1;
            
            .validation-field {
              font-weight: 500;
              margin-bottom: 2px;
            }
            
            .validation-message {
              font-size: 13px;
              color: #606266;
            }
          }
        }
      }
    }
  }
}

.validation-messages {
  margin: 15px 0;
  
  .validation-message {
    display: flex;
    align-items: flex-start;
    padding: 12px;
    border-radius: 6px;
    margin-bottom: 8px;
    font-size: 14px;
    
    .el-icon {
      margin-right: 8px;
      margin-top: 2px;
      flex-shrink: 0;
    }
    
    &.error-message {
      background-color: #fef0f0;
      border: 1px solid #f56c6c;
      color: #f56c6c;
    }
    
    &.warning-message {
      background-color: #fdf6ec;
      border: 1px solid #e6a23c;
      color: #e6a23c;
      
      .warning-content {
        display: flex;
        flex-direction: column;
        gap: 4px;
      }
    }
    
    &.special-message {
      background-color: #f0f9ff;
      border: 1px solid #409eff;
      color: #409eff;
    }
  }
}

/* 字段验证样式 */
.info-value.error-field {
  background-color: #fef0f0;
  border: 1px solid #f56c6c;
  color: #f56c6c;
  border-radius: 4px;
  padding: 2px 6px;
}

.info-value.warning-field {
  background-color: #fdf6ec;
  border: 1px solid #e6a23c;
  color: #e6a23c;
  border-radius: 4px;
  padding: 2px 6px;
}

</style>