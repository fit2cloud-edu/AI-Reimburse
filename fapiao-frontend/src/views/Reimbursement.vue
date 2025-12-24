<template>
  <div class="reimbursement-container">
    <!-- 用户信息和退出按钮 -->
    <div class="user-info-bar">
      <div class="user-info">
        <el-avatar size="small" :src="userAvatar" />
        <span class="user-name">{{ authStore.userName || authStore.userInfo?.name || '用户' }}</span>
        <el-tag size="small" type="info">{{ authStore.userInfo?.departmentName || authStore.userInfo?.department || '未设置部门' }}</el-tag>
      </div>
      <el-button type="text" @click="handleLogout" class="logout-btn">
        <el-icon><SwitchButton /></el-icon>
        退出登录
      </el-button>
    </div>

    <!-- 流程步骤 -->
    <FlowSteps 
      :steps="steps" 
      :current-step="currentStep"
      @step-click="handleStepClick"
    />
    
    <!-- 选择类型步骤 -->
    <div v-if="currentStep === 0" class="type-select-step">
      <h2>请选择报销类型</h2>
      <div class="type-cards">
        <el-card 
          v-for="type in reimbursementTypes" 
          :key="type.value"
          :class="['type-card', { 'selected': formType === type.value }]"
          @click="selectFormType(type.value)"
        >
          <div class="type-content">
            <el-icon size="40">
              <component :is="type.icon" />
            </el-icon>
            <h3>{{ type.label }}</h3>
            <p>{{ type.description }}</p>
          </div>
        </el-card>
      </div>
      
      <div class="step-actions">
        <el-button type="primary" @click="nextStep" :disabled="!formType">
          下一步：上传文件
        </el-button>
      </div>
    </div>
    
    <!-- 上传文件步骤 -->
    <div v-if="currentStep === 1" class="upload-step">
      <h2>上传发票文件</h2>
      
      <!-- 报销事由输入 -->
      <div class="reason-section">
        <el-input
          v-model="formReimbursementReason"
          type="textarea"
          :rows="3"
          placeholder="请输入报销事由（必填）"
          maxlength="200"
          show-word-limit
        />
      </div>
      
      <!-- 文件上传组件 -->
      <FileUploader
        v-model="selectedFiles"
        :uploading="uploading"
        :show-actions="false"
        @upload="handleUpload"
        @clear="clearSelectedFiles"
      />
      
      <!-- 统一的上传统计和操作区域 -->
      <div v-if="selectedFiles.length > 0" class="upload-controls">
        <div class="stats-container">
          <el-tag type="info" size="large">总数量: {{ selectedFiles.length }}</el-tag>
          <el-tag type="success" size="large">文档: {{ fileStats.documents }}</el-tag>
          <el-tag type="warning" size="large">图片: {{ fileStats.images }}</el-tag>
        </div>
        <div class="actions-container">
          <el-button 
            @click="clearSelectedFiles"
            size="large"
          >
            <el-icon><Delete /></el-icon>
            清空
          </el-button>
        </div>
      </div>
      
      <div class="step-actions">
        <el-button @click="prevStep">上一步</el-button>
        <el-button 
          type="primary" 
          @click="handleUpload"
          :loading="uploading"
          :disabled="selectedFiles.length === 0 || !formReimbursementReason.trim()"
        >
          {{ uploading ? '上传中...' : '确认上传' }}
        </el-button>
      </div>
    </div>
    
    <!-- 填写信息步骤 -->
    <div v-if="currentStep === 2" class="fill-form-step">
      <h2>填写报销信息</h2>
      
      <!-- 报销信息表单 -->
      <el-form 
        ref="reimbursementFormRef"
        :model="reimbursementForm"
        label-position="top" 
        class="reimbursement-form"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="报销类型" prop="formType">
              <div class="form-field-content">
                <el-tag>{{ formTypeMap[reimbursementForm.formType] }}</el-tag>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="提交人员" prop="submitter">
              <div class="form-field-content">
                <el-input v-model="reimbursementForm.submitter" disabled />
              </div>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="法人实体" prop="legalEntity" required>
              <el-select 
                v-model="reimbursementForm.legalEntity"
                placeholder="请选择法人实体"
              >
                <el-option 
                  v-for="entity in legalEntities" 
                  :key="entity.value"
                  :label="entity.label"
                  :value="entity.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="报销日期" prop="reimbursementDate" required>
              <el-date-picker
                v-model="reimbursementForm.reimbursementDate"
                type="date"
                placeholder="选择日期"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
              />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="区域"  required>
              <el-select
                v-model="reimbursementForm.region"
                placeholder="请选择区域"
                @change="handleRegionChange"
                style="width: 100%"
              >
                <el-option 
                  v-for="region in regions" 
                  :key="region"
                  :label="region"
                  :value="region"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="费用承担部门"  required>
              <el-select
                v-model="reimbursementForm.costDepartment"
                placeholder="请选择部门"
                @change="handleDepartmentChange"
                style="width: 100%"
              >
                <el-option 
                  v-for="dept in getDepartmentOptions()" 
                  :key="dept.value"
                  :label="dept.label"
                  :value="dept.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-form-item label="报销事由" prop="formReimbursementReason" required>
          <el-input
            v-model="reimbursementForm.formReimbursementReason"
            type="textarea"
            :rows="3"
            placeholder="请输入报销事由"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        
        <!-- 客成差旅特殊字段 -->
        <div v-if="formType === '客成差旅报销单'" class="travel-section">
          <el-divider>出差信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="客户名称" prop="customerName">
                <el-input v-model="reimbursementForm.customerName" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="未签单客户" prop="unsignedCustomer">
                <el-input v-model="reimbursementForm.unsignedCustomer" />
              </el-form-item>
            </el-col>
          </el-row>
          
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="开始时间" required>
                <el-date-picker
                  v-model="reimbursementForm.travelStartDate"
                  type="date"
                  placeholder="选择日期"
                  format="YYYY-MM-DD"
                  value-format="YYYY-MM-DD"
                  @change="calculateTravelDays"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="开始时段">
                <el-select 
                  v-model="reimbursementForm.travelStartPeriod"
                  @change="calculateTravelDays"
                >
                  <el-option label="上午" value="上午" />
                  <el-option label="下午" value="下午" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="结束时间" required>
                <el-date-picker
                  v-model="reimbursementForm.travelEndDate"
                  type="date"
                  placeholder="选择日期"
                  format="YYYY-MM-DD"
                  value-format="YYYY-MM-DD"
                  @change="calculateTravelDays"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="结束时段">
                <el-select 
                  v-model="reimbursementForm.travelEndPeriod"
                  @change="calculateTravelDays"
                >
                  <el-option label="上午" value="上午" />
                  <el-option label="下午" value="下午" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          
          <el-form-item label="出差天数">
            <el-input 
              v-model="reimbursementForm.travelDays" 
              disabled
              :value="calculatedTravelDays"
            />
          </el-form-item>

          <!-- 出差补贴申请单开关 -->
          <el-form-item label="出差补贴申请" prop="submitTravelSubsidy">
            <div class="travel-subsidy-switch">
              <el-switch
                v-model="reimbursementForm.submitTravelSubsidy"
                active-text="提交出差补贴申请单"
                inactive-text="跳过出差补贴申请单"
                :active-value="true"
                :inactive-value="false"
              />
              <div class="switch-description">
                <small class="form-text text-muted">
                  关闭后将只提交出差申请单和报销单，不提交补贴申请单
                </small>
              </div>
            </div>
          </el-form-item>

        </div>
      </el-form>
      
      <!-- 发票信息列表 -->
      <div class="invoice-list">
        <h3>发票信息（{{ invoiceInfos.length }}张）</h3>
        
        <div class="invoice-cards">
          <InvoiceCard
            v-for="(invoice, index) in invoiceInfos"
            :key="invoice.id || index"
            :invoice="invoice"
            :index="index"
            :active-index="activeInvoiceIndex"
            :validation="invoiceValidations[index]"
            :reimbursement-types="expenseTypes"
            @select="handleInvoiceSelect"
            @update="handleInvoiceUpdate"
            @delete="handleInvoiceDelete"
          />
        </div>
        

        <!-- 继续上传 -->
        <div v-if="!showContinueUpload" class="continue-upload">
          <el-button type="primary" @click="handleShowContinueUpload">
            <el-icon><Plus /></el-icon>
            继续上传发票
          </el-button>
        </div>

        <!-- 继续上传界面 -->
        <div v-if="showContinueUpload" class="continue-upload-section">
          <h3>继续上传发票</h3>
          
          <!-- 文件上传组件 -->
          <FileUploader
            v-model="continueUploadFiles"
            :uploading="continueUploading"
            :show-actions="false"
            @upload="handleContinueUpload"
            @clear="clearContinueUploadFiles"
          />
          
          <!-- 统一的上传统计和操作区域 -->
          <div v-if="continueUploadFiles.length > 0" class="upload-controls">
            <div class="stats-container">
              <el-tag type="info" size="large">本次上传数量: {{ continueUploadFiles.length }}</el-tag>
              <el-tag type="success" size="large">文档: {{ continueFileStats.documents }}</el-tag>
              <el-tag type="warning" size="large">图片: {{ continueFileStats.images }}</el-tag>
            </div>
            <div class="actions-container">
              <el-button 
                @click="clearContinueUploadFiles"
                size="large"
              >
                <el-icon><Delete /></el-icon>
                清空
              </el-button>
            </div>
          </div>
          
          <!-- 继续上传操作按钮 -->
          <div class="continue-upload-actions">
            <el-button @click="handleCancelContinueUpload">上一步</el-button>
            <el-button 
              type="primary" 
              @click="handleContinueUpload"
              :loading="continueUploading"
              :disabled="continueUploadFiles.length === 0"
            >
              {{ continueUploading ? '上传中...' : '确认上传' }}
            </el-button>
          </div>
        </div>
      </div>
      
      <!-- 底部操作栏 -->
      <div class="bottom-actions">
        <div class="total-amount">
          <span class="label">总计金额：</span>
          <span class="amount">{{ totalAmount }}</span>
        </div>

        <!-- 提交按钮区域 -->
        <div class="submit-section">
          <div class="submit-info">
            <!-- 错误提示 -->
            <div v-if="hasSevereErrors" class="submit-error">
              <el-icon><Warning /></el-icon>
              <span>有发票存在严重问题，禁止提交</span>
            </div>
            
            <!-- 警告提示 -->
            <div v-else-if="hasWarnings" class="submit-warning">
              <el-icon><InfoFilled /></el-icon>
              <span>有发票存在问题，请写清楚原因</span>
            </div>
            
            <!-- 特殊票据提示 -->
            <div v-else-if="hasSpecialInvoices" class="submit-info">
              <el-icon><InfoFilled /></el-icon>
              <span>有特殊票据需要人工审核</span>
            </div>
          </div>
          
          <div class="step-actions">
            <el-button @click="prevStep">上一步</el-button>
            <el-button 
              type="primary" 
              :disabled="!canSubmit || hasSevereErrors"
              :loading="submitting"
              @click="submitReimbursement"
            >
              {{ submitting ? '提交中...' : '提交审批' }}
            </el-button>
          </div>
        </div>


      </div>
    </div>
    
    <!-- 进度弹窗 -->
    <ProgressModal
      v-model="showProgressModal"
      :progress="submitProgress"
      :title="submitProgress.title"
    />
    
    <!-- 成功提示 -->
    <el-dialog
      v-model="submitSuccess"
      title="提交成功"
      width="400px"
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
    >
      <div class="success-dialog">
        <el-icon color="#67C23A" size="60">
          <CircleCheck />
        </el-icon>
        <p class="success-message">报销申请已成功提交！</p>
        <p class="success-hint">请等待审批结果</p>
      </div>
      <template #footer>
        <el-button type="primary" @click="handleSubmitSuccess">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useAuthStore } from '../store/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Document, 
  Money, 
  Plus,
  CircleCheck,
  OfficeBuilding,
  Timer,
  SwitchButton  // 退出图标
} from '@element-plus/icons-vue'

// API
import { 
  uploadInvoiceFiles,
  uploadSingleInvoice, 
  submitReimbursement as apiSubmitReimbursement,
  getDepartmentList,
  getUserDepartmentInfo,
  getRegionByDepartment,
  type ReimbursementSubmitData,
  type Department
} from '../api'

// 导入验证器
import { validators } from '../utils/validators'

// 类型定义
interface InvoiceInfo {
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
}

interface ValidationResult {
  valid: boolean
  violations?: Array<{
    severity: 'ERROR' | 'WARNING'
    field: string
    message: string
    affectedField?: string
  }>
  fieldValidationMap?: Record<string, any>
}

// 报销类型接口定义
interface ReimbursementType {
  value: string
  label: string
  description: string
  icon: any
}

// 状态管理
const authStore = useAuthStore()

// 响应式数据
const currentStep = ref(0)
const formType = ref('')
const selectedFiles = ref<File[]>([])
const uploading = ref(false)
const invoiceInfos = ref<InvoiceInfo[]>([])
const activeInvoiceIndex = ref(-1)
const showContinueUpload = ref(false)
const submitting = ref(false)
const submitSuccess = ref(false)
const showProgressModal = ref(false)
const formReimbursementReason = ref('') 

// 部门相关数据
const departments = ref<Department[]>([])
const departmentTree = ref<Department[]>([])
const filteredDepartments = ref<Department[]>([])
const regions = ref<string[]>([])
const currentDepartmentPath = ref<string[]>([])

const continueUploadFiles = ref<File[]>([])
const continueUploading = ref(false)
const continueUploadReason = ref('')
const uploadSessionId = ref('')

const continueFileStats = computed(() => {
  const stats = { images: 0, documents: 0 }
  continueUploadFiles.value.forEach(file => {
    const extension = file.name.split('.').pop()?.toLowerCase()
    if (['jpg', 'jpeg', 'png'].includes(extension || '')) {
      stats.images++
    } else if (['pdf'].includes(extension || '')) {
      stats.documents++
    }
  })
  return stats
})

// 计算用户头像
const userAvatar = computed(() => {
  const userName = authStore.userName || authStore.userInfo?.name || ''
  if (userName) {
    // 如果用户名包含中文字符，确保能正确显示
    return `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(userName)}`
  }
  return 'https://api.dicebear.com/7.x/initials/svg?seed=User'
})

onMounted(() => {
  // 自动填充用户信息到表单
  if (authStore.userInfo) {
    reimbursementForm.submitter = authStore.userInfo.name || ''
    reimbursementForm.region = authStore.userInfo.region || ''
    reimbursementForm.costDepartment = authStore.userInfo.departmentId || ''
  }
})

// 退出登录处理
const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    authStore.logout()
    // 跳转到登录页
    window.location.href = '/login'
  } catch {
    // 用户取消退出
  }
}


// 步骤定义
const steps = [
  { title: '选择类型', icon: Document },
  { title: '上传文件', icon: Money },
  { title: '填写信息', icon: Timer }
]

// 报销类型映射
const formTypeMap: Record<string, string> = {
  '日常报销单': '日常报销',
  '客成差旅报销单': '客成差旅报销'
}

// 初始化数据
const reimbursementForm = reactive({
  formType: '',
  submitter: authStore.userName || '',
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
  travelDays: '0',
  submitTravelSubsidy: true // 出差补贴申请单开关，默认打开
})

// 报销类型定义 - 修复类型错误
const reimbursementTypes: ReimbursementType[] = [
  {
    value: '日常报销单',
    label: '日常报销',
    description: '日常办公费用报销',
    icon: OfficeBuilding
  },
  {
    value: '客成差旅报销单', 
    label: '客成差旅报销',
    description: '客户项目差旅费用报销',
    icon: Timer
  }
]

// 费用类型列表（用于InvoiceCard组件）
const expenseTypes = [
  '交通费', '福利费', '办公费', '快递费', '业务招待费', 
  '礼品费', '技术服务费', '会议费', '培训费', '中介服务费',
  '推广费', 'POC费用', '咨询费', '中介机构费', '差旅成本'
]

const legalEntities = [
  { label: '杭州飞致云信息科技有限公司（CODE1）', value: '杭州飞致云信息科技有限公司（CODE1）' }
]

// 校验数据
const invoiceValidations = ref<Record<number, ValidationResult>>({})

// 计算属性
const fileStats = computed(() => {
  const stats = { images: 0, documents: 0 }
  selectedFiles.value.forEach(file => {
    const extension = file.name.split('.').pop()?.toLowerCase()
    if (['jpg', 'jpeg', 'png'].includes(extension || '')) {
      stats.images++
    } else if (['pdf'].includes(extension || '')) {
      stats.documents++
    }
  })
  return stats
})

// 初始化报销表单
const initReimbursementForm = () => {
  // 设置提交人员
  reimbursementForm.submitter = authStore.userName || ''
  
  console.log('initReimbursementForm开始执行，当前表单值:', {
    region: reimbursementForm.region,
    costDepartment: reimbursementForm.costDepartment
  })

  // 只有在用户部门信息API调用失败时，才使用登录信息作为后备
  if (!reimbursementForm.region && !reimbursementForm.costDepartment && authStore.userInfo) {
    const userInfo = authStore.userInfo
    
    console.log('使用登录信息作为后备填充，userInfo:', userInfo)

    // 优先使用 departmentStructure 中的信息
    if (userInfo.departmentStructure){
        reimbursementForm.region = userInfo.departmentStructure.region || ''
        reimbursementForm.costDepartment = userInfo.departmentStructure.departmentId || ''
          console.log('使用departmentStructure填充:', {
            region: reimbursementForm.region,
            department: reimbursementForm.costDepartment
          })
    }else{
      // 后备方案：使用直接字段
      reimbursementForm.region = userInfo.region || ''
      reimbursementForm.costDepartment = userInfo.departmentId || ''
      console.log('使用直接字段填充:', {
        region: reimbursementForm.region,
        department: reimbursementForm.costDepartment
      })
    }
    
    if (reimbursementForm.costDepartment) {
      currentDepartmentPath.value = getDepartmentPath(departments.value, reimbursementForm.costDepartment)
      console.log('更新部门路径:', currentDepartmentPath.value)
    }
    } else {
    console.log('表单已有值或用户信息为空，跳过填充')
  }
    
}

const totalAmount = computed(() => {
  let total = 0
  invoiceInfos.value.forEach(invoice => {
    const amount = parseFloat(invoice.totalAmount?.replace('元', '') || '0')
    total += isNaN(amount) ? 0 : amount
  })
  return `¥${total.toFixed(2)}`
})

const calculatedTravelDays = computed(() => {
  if (!reimbursementForm.travelStartDate || !reimbursementForm.travelEndDate) {
    return '0'
  }

  const startDate = new Date(reimbursementForm.travelStartDate)
  const endDate = new Date(reimbursementForm.travelEndDate)
  const startPeriod = reimbursementForm.travelStartPeriod
  const endPeriod = reimbursementForm.travelEndPeriod
  
  // 如果开始日期晚于结束日期，返回0
  if (startDate > endDate) {
    return '0'
  }
  
  // 计算完整的天数差异（不包括开始和结束日期的部分天数）
  const timeDiff = endDate.getTime() - startDate.getTime()
  const fullDays = Math.floor(timeDiff / (1000 * 60 * 60 * 24))
  
  let totalDays = 0
  
  if (fullDays === 0) {
    // 同一天的情况
    if (startPeriod === '上午' && endPeriod === '下午') {
      totalDays = 0.5  // 同一天上午→下午=0.5天
    } else {
      // 上午→上午=0天，下午→下午=0天
      totalDays = 0
    }
  } else {
    // 跨天的情况
    if (startPeriod === '上午' && endPeriod === '上午') {
      totalDays = fullDays  // 上午→次日上午=1天（fullDays=1时）
    } else if (startPeriod === '上午' && endPeriod === '下午') {
      totalDays = fullDays + 0.5  // 上午→次日下午=1.5天（fullDays=1时）
    } else if (startPeriod === '下午' && endPeriod === '上午') {
      totalDays = fullDays - 0.5  // 下午→次日上午=0.5天（fullDays=1时）
    } else if (startPeriod === '下午' && endPeriod === '下午') {
      totalDays = fullDays  // 下午→次日下午=1天（fullDays=1时）
    }
  }
  // 确保天数不小于0
  totalDays = Math.max(totalDays, 0)

  return totalDays.toFixed(1)
})

// 计算提交状态
const hasSevereErrors = computed(() => {
  return Object.values(invoiceValidations.value).some(validation => {
    if (!validation) return false
    return validators.hasSevereValidationError(validation, invoiceInfos.value[0] || {})
  })
})

const hasWarnings = computed(() => {
  return Object.values(invoiceValidations.value).some(validation => {
    if (!validation) return false
    const violations = validation.violations || []
    return violations.some((v: any) => 
      (v.affectedField === '开票日期' && v.severity === 'WARNING' && v.message?.includes('超过1年')) ||
      (v.field === 'invoice_verification' && v.message?.includes('超过该张发票当日查验次数'))
    )
  })
})

const hasSpecialInvoices = computed(() => {
  return Object.values(invoiceValidations.value).some((validation, index) => {
    if (!validation) return false
    return validators.isSpecialInvoice(validation, invoiceInfos.value[index] || {})
  })
})

const canSubmit = computed(() => {
  // 基本提交条件检查
  if (!formType.value || selectedFiles.value.length === 0) return false
  if (!reimbursementForm.legalEntity || !reimbursementForm.reimbursementDate) return false
  if (!reimbursementForm.region || !reimbursementForm.costDepartment) return false
  if (!reimbursementForm.formReimbursementReason?.trim()) return false
  
  // 检查发票信息完整性
  if (invoiceInfos.value.some(invoice => !invoice.reimbursementType || !invoice.totalAmount)) {
    return false
  }
  
  // 检查客成差旅的必要字段
  if (formType.value === '客成差旅报销单') {
    if (!reimbursementForm.travelStartDate || !reimbursementForm.travelEndDate) {
      return false
    }
  }
  
  return true
})

const submitProgress = reactive({
  title: '提交中',
  currentStep: 0,
  totalSteps: 1,
  steps: [],
  message: ''
})

// 生命周期
onMounted(async () => {
    // 添加延迟和条件检查
  await nextTick()
  
  // 只有在用户已登录且有必要信息时才初始化
  if (authStore.isLoggedIn && authStore.userInfo?.userid) {
    await initializeApp()
  } else {
    console.warn('用户未登录或缺少必要信息，跳过初始化')
  }
})

const loadDepartments = async () => {
  try {
    const result = await getDepartmentList()
    console.log('部门列表API返回:', result)
    if (result.success && result.data) {
      departments.value = result.data
      departmentTree.value = buildDepartmentTree(result.data)
      filteredDepartments.value = result.data
      console.log('部门数据:', departments.value)
        
      // 提取所有区域（去重）
      const allRegions = result.data
        .map(dept => dept.region)
        .filter((region): region is string => !!region)
        .filter((region, index, self) => self.indexOf(region) === index)
      regions.value = allRegions
      console.log('提取的区域列表:', regions.value)

      // 如果区域列表为空，添加默认区域
      if (regions.value.length === 0) {
        console.warn('区域列表为空，添加默认区域')
        regions.value = ['华东区域', '华南区域', '华中区域', '华北区域']
      }
    } else {
      console.error('部门列表API返回失败:', result)
      // 添加默认区域作为后备
      regions.value = ['华东区域', '华南区域', '华中区域', '华北区域']
    }
  } catch (error) {
    console.error('加载部门列表失败:', error)
    // 错误时添加默认区域
    regions.value = ['华东区域', '华南区域', '华中区域', '华北区域']
  }
}

const loadUserDepartmentInfo = async (userId: string) => {
  try {
    console.log('开始获取用户部门信息，userId:', userId)
    const result = await getUserDepartmentInfo(userId)
    console.log('用户部门信息API返回:', result)

    if (result.data) {
      // 使用result.data中的字段
      const userDeptInfo = result.data
      console.log('后端返回的用户部门信息详情:', userDeptInfo)
      
      // 检查是否有有效的部门信息 - 修复判断逻辑
      if (userDeptInfo.departmentId) {
        // 优先从region字段获取区域信息
        let region: string | null | undefined = userDeptInfo.region
        
        // 如果region字段为空，尝试从fullDepartmentPath中提取区域信息
        if (!region && userDeptInfo.fullDepartmentPath) {
          const regionMatch = userDeptInfo.fullDepartmentPath.match(/^([^-]+区域)/)
          region = regionMatch ? regionMatch[1] : null
          console.log('从fullDepartmentPath中提取的区域信息:', region)
        }
        
        reimbursementForm.region = region || ''
        reimbursementForm.costDepartment = userDeptInfo.departmentId
        
        console.log('从API成功获取并填充部门信息:', {
          region: reimbursementForm.region,
          department: reimbursementForm.costDepartment
        })

        // 设置部门路径
        currentDepartmentPath.value = getDepartmentPath(departments.value, userDeptInfo.departmentId)
        
        console.log('用户部门信息已填充到表单:', {
          region: reimbursementForm.region,
          department: reimbursementForm.costDepartment,
          departmentPath: currentDepartmentPath.value
        })
      } else {
        console.warn('API返回的部门信息不完整，缺少departmentId:', userDeptInfo)
      }
    } else {
      console.warn('用户部门信息API返回失败或数据为空:', result)
    }
  }catch (error) {
    console.error('获取用户部门信息失败:', error)
  }
}

const initializeApp = async () => {
  try {
    console.log('initializeApp开始执行，当前用户信息:', authStore.userInfo)
    
    // 检查必要信息
    if (!authStore.userInfo?.userid) {
      console.warn('用户ID不存在，跳过API调用')
      initReimbursementForm() // 只使用登录信息填充表单
      return
    }
    
    // 设置提交人员
    reimbursementForm.submitter = authStore.userInfo?.name || '未知用户'
    
    // 先使用登录信息填充表单（快速显示）
    initReimbursementForm()
    
    // 延迟调用API（避免与登录重定向前后冲突）
    setTimeout(async () => {
      try {
        // 获取部门列表
        await loadDepartments()
        
        // 获取用户部门信息（添加空值检查）
        if (authStore.userInfo?.userid) {
          await loadUserDepartmentInfo(authStore.userInfo.userid)
        } else {
          console.warn('用户信息为空，跳过用户部门信息加载')
        }
      } catch (error) {
        console.error('API调用失败，但已使用登录信息填充表单:', error)
      }
    }, 100)
    
  } catch (error) {
    console.error('初始化失败:', error)
    // 即使失败也要确保表单有基本数据
    initReimbursementForm()
  }
}

const handleRegionChange = (value: string) => {
  console.log('区域变化:', value)
  
  if (value) {
    // 过滤该区域下的部门
    const regionDepartments = filterDepartmentsByRegion(departments.value, value)
    console.log('该区域下的部门:', regionDepartments)
    
    if (regionDepartments.length > 0) {
      // 情况1：区域有下属部门（如"华南"）
      // 部门选择器只能选择该区域下的部门
      filteredDepartments.value = regionDepartments
      
      // 清空已选择的部门，让用户重新选择
      reimbursementForm.costDepartment = ''
      currentDepartmentPath.value = []
      console.log('区域有下属部门，清空部门选择，等待用户选择')
    } else {
      // 情况2：区域没有下属部门（如"华东"）
      // 自动将该区域作为部门填充
      
      // 查找该区域对应的第一级部门
      const firstLevelDeptInRegion = departments.value.find(dept => 
        dept.region === value && !dept.parentId
      )
      
      if (firstLevelDeptInRegion) {
        reimbursementForm.costDepartment = firstLevelDeptInRegion.id.toString()
        currentDepartmentPath.value = [firstLevelDeptInRegion.name]
        console.log('使用区域下的第一级部门:', firstLevelDeptInRegion.name)
      } else {
        // 如果没有找到第一级部门，查找区域名称包含在部门名称中的部门
        const regionAsDepartment = departments.value.find(dept => 
          dept.name.includes(value.replace('区域', '')) || 
          dept.region === value
        )
        
        if (regionAsDepartment) {
          reimbursementForm.costDepartment = regionAsDepartment.id.toString()
          currentDepartmentPath.value = [regionAsDepartment.name]
          console.log('区域作为部门填充:', regionAsDepartment.name)
        } else {
          // 如果仍然没有找到，清空部门选择
          reimbursementForm.costDepartment = ''
          currentDepartmentPath.value = []
          console.log('未找到对应的区域部门，清空部门选择')
        }
      }
    }
  } else {
    // 区域为空时，显示所有部门
    filteredDepartments.value = departments.value
    reimbursementForm.costDepartment = ''
    currentDepartmentPath.value = []
  }
}

const handleDepartmentChange = async (value: string) => {
  if (value) {
    try {
      // 调用后端接口获取部门对应的区域信息
      const result = await getRegionByDepartment(value)
      console.log('部门区域API返回:', result)
      
      if (result.success) {
        // 后端返回的数据结构：data为null，message包含区域信息
        const region = result.message || result.data
        console.log('提取的区域信息:', region)
        
        if (region) {
          // 使用后端返回的区域信息
          reimbursementForm.region = region
          console.log('设置区域为:', region)
        } else {
          // 如果区域信息为空，使用前端数据
          console.warn('后端返回的区域信息为空，使用前端数据')
          const selectedDept = departments.value.find(dept => dept.id.toString() === value)
          if (selectedDept && selectedDept.region) {
            reimbursementForm.region = selectedDept.region
            console.log('使用前端数据设置区域为:', selectedDept.region)
          }
        }
      } else {
        // 如果后端接口失败，使用前端数据
        console.warn('后端接口失败，使用前端数据')
        const selectedDept = departments.value.find(dept => dept.id.toString() === value)
        if (selectedDept && selectedDept.region) {
          reimbursementForm.region = selectedDept.region
          console.log('使用前端数据设置区域为:', selectedDept.region)
        }
      }
      
      // 更新部门路径
      currentDepartmentPath.value = getDepartmentPath(departments.value, value)
    } catch (error) {
      console.error('获取部门区域信息失败:', error)
      // 错误处理：使用前端数据
      const selectedDept = departments.value.find(dept => dept.id.toString() === value)
      if (selectedDept && selectedDept.region) {
        reimbursementForm.region = selectedDept.region
        console.log('错误时使用前端数据设置区域为:', selectedDept.region)
      }
      
      currentDepartmentPath.value = getDepartmentPath(departments.value, value)
    }
  } else {
    currentDepartmentPath.value = []
  }
}

// 构建部门树形结构
const buildDepartmentTree = (departments: Department[]): Department[] => {
  const departmentMap = new Map<string, Department & { children?: Department[] }>()
  const rootDepartments: (Department & { children?: Department[] })[] = []
  
  // 创建映射
  departments.forEach(dept => {
    departmentMap.set(dept.id.toString(), { ...dept, children: [] })
  })
  
  // 构建树形结构
  departments.forEach(dept => {
    const department = departmentMap.get(dept.id.toString())
    if (dept.parentId && departmentMap.has(dept.parentId.toString())) {
      const parent = departmentMap.get(dept.parentId.toString())
      if (parent && department) {
        if (!parent.children) parent.children = []
        parent.children.push(department)
      }
    } else {
      if (department) {
        rootDepartments.push(department)
      }
    }
  })
  
  return rootDepartments
}

// 根据区域过滤部门
const filterDepartmentsByRegion = (departments: Department[], region: string): Department[] => {
  if (!region) return departments
  
  // 过滤该区域下的所有部门（包括子部门）
  return departments.filter(dept => dept.region === region)
}

// 获取部门层级路径
const getDepartmentPath = (departments: Department[], departmentId: string): string[] => {
  const path: string[] = []
  let currentId = departmentId
  
  while (currentId) {
    const dept = departments.find(d => d.id.toString() === currentId)
    if (dept) {
      path.unshift(dept.name)
      currentId = dept.parentId?.toString() || ''
    } else {
      break
    }
  }
  
  return path
}

// 获取部门层级选项
const getDepartmentOptions = () => {
  return filteredDepartments.value.map(dept => ({
    value: dept.id.toString(),
    label: dept.name,
    region: dept.region
  }))
}



const selectFormType = (type: string) => {
  formType.value = type
  reimbursementForm.formType = type
}

const nextStep = () => {
  if (currentStep.value < 2) {
    // 在进入下一步时同步报销事由数据
    if (currentStep.value === 1 && formReimbursementReason.value.trim()) {
      reimbursementForm.formReimbursementReason = formReimbursementReason.value
    }
    currentStep.value++
  }
}

const prevStep = () => {
  if (currentStep.value > 0) {
    currentStep.value--
  }
}

const handleStepClick = (index: number) => {
  if (index < currentStep.value) {
    currentStep.value = index
  }
}

const handleUpload = async () => {
  if (selectedFiles.value.length === 0) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  
  if (!formReimbursementReason.value.trim()) {
    ElMessage.warning('请输入报销事由')
    return
  }
  
  uploading.value = true
  
  try {
    reimbursementForm.formReimbursementReason = formReimbursementReason.value

    // 显示上传进度提示
    ElMessage.info('发票上传中，请稍候...（处理时间可能较长）')

    const result = await uploadInvoiceFiles(
      selectedFiles.value, 
      formType.value,
      formReimbursementReason.value
    )
    
    if (result.success) {
      // 处理返回的发票信息
      if (result.data?.invoiceInfos) {
        const data = result.data

        // 添加调试日志，确认后端返回的mediaIds
        console.log('后端返回的mediaIds:', data.mediaIds)
        console.log('后端返回的invoiceInfos数量:', data.invoiceInfos.length)
        
        // 将逗号分隔的mediaIds字符串分割成数组
        const mediaIdsArray = data.mediaIds ? data.mediaIds.split(',') : []
        console.log('分割后的mediaIds数组:', mediaIdsArray)

        invoiceInfos.value = data.invoiceInfos.map((invoice: any, index: number) => ({
          // 确保字段名正确映射
          buyerName: invoice.buyerName || '',
          buyerCode: invoice.buyerCode || '',
          invoiceNo: invoice.invoiceNumber || '',  // 将invoiceNumber映射到invoiceNo
          invoiceDate: invoice.invoiceDate || '',
          sellerName: invoice.sellerName || '',
          totalAmount: invoice.totalAmount || '',
          // 添加id字段
          id: `invoice_${Date.now()}_${index}`,
          reimbursementType: formType.value === '客成差旅报销单' ? '差旅成本' : (invoice.reimbursementType || ''),
          consumptionDate: invoice.invoiceDate || reimbursementForm.reimbursementDate,
          mediaId: invoice.mediaId || (mediaIdsArray[index] || ''),
          // 保留后端返回的其他字段
          ...invoice
        }))
        
        // 处理校验数据
        if (data.validationResult?.results) {
          data.validationResult.results.forEach((item: any, index: number) => {
            if (item.validationResult) {
              invoiceValidations.value[index] = item.validationResult
            }
          })
        }
      }
      
      ElMessage.success(`识别到${invoiceInfos.value.length}张发票`)
      nextStep()
    } else {
      ElMessage.error(result.message || '上传失败')
    }
  } catch (error) {
    // 检查是否是超时错误
    if ((error as Error).message.includes('timeout')) {
      ElMessage.error('上传超时，请减少文件数量或稍后重试')
    } else {
      ElMessage.error('上传失败: ' + (error as Error).message)
    }
  } finally {
    uploading.value = false
  }
}

const clearSelectedFiles = () => {
  selectedFiles.value = []
  ElMessage.success('已清除文件')
}

const calculateTravelDays = () => {
  reimbursementForm.travelDays = calculatedTravelDays.value
}

const handleInvoiceSelect = (index: number) => {
  activeInvoiceIndex.value = activeInvoiceIndex.value === index ? -1 : index
}

const handleInvoiceUpdate = (index: number, field: string, value: any) => {
  if (invoiceInfos.value[index]) {
    invoiceInfos.value[index][field as keyof InvoiceInfo] = value
    
    // 如果是金额字段，重新计算总金额
    if (field === 'totalAmount') {
      // 触发重新计算
    }

    // 如果是消费日期字段，同时更新开票日期（保持同步）
    if (field === 'consumptionDate') {
      invoiceInfos.value[index].invoiceDate = value
    }
  }
}

const generateSessionId = () => {
  return `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
}

// 初始化会话ID
const initializeSession = () => {
  if (!uploadSessionId.value) {
    uploadSessionId.value = generateSessionId()
  }
}

// 显示继续上传界面
const handleShowContinueUpload = () => {
  showContinueUpload.value = true
  initializeSession() // 初始化会话ID
}

// 继续上传处理
const handleContinueUpload = async () => {
  if (continueUploadFiles.value.length === 0) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  
  continueUploading.value = true
  
  try {
    // 使用连续上传模式，逐个上传文件
    const results = []
    
    for (let i = 0; i < continueUploadFiles.value.length; i++) {
      const file = continueUploadFiles.value[i]
      // 添加文件存在性检查
      if (!file) {
        console.warn(`第 ${i + 1} 个文件不存在，跳过`)
        continue
      }

      const isLast = i === continueUploadFiles.value.length - 1
      
      try {
        const result = await uploadSingleInvoice(
          file,
          uploadSessionId.value,
          isLast,
          formType.value
        )
        
        if (result.success) {
          results.push(result)
          
          // 如果是最后一个文件，处理返回的发票信息
          if (isLast && result.data?.invoiceInfos) {
            const data = result.data
            
            // 处理返回的发票信息
            const newInvoices = data.invoiceInfos.map((invoice: any, index: number) => ({
              buyerName: invoice.buyerName || '',
              buyerCode: invoice.buyerCode || '',
              invoiceNo: invoice.invoiceNumber || '',
              invoiceDate: invoice.invoiceDate || '',
              sellerName: invoice.sellerName || '',
              totalAmount: invoice.totalAmount || '',
              id: `invoice_${Date.now()}_${invoiceInfos.value.length + index}`,
              reimbursementType: formType.value === '客成差旅报销单' ? '差旅成本' : (invoice.reimbursementType || ''),
              consumptionDate: invoice.invoiceDate || reimbursementForm.reimbursementDate,
              mediaId: invoice.mediaId || (data.mediaIds ? data.mediaIds.split(',')[index] : ''),
              ...invoice
            }))
            
            // 添加新发票到现有列表
            invoiceInfos.value.push(...newInvoices)
            
            // 处理校验数据
            if (data.validationResult?.results) {
              data.validationResult.results.forEach((item: any, index: number) => {
                const globalIndex = invoiceInfos.value.length - newInvoices.length + index
                if (item.validationResult) {
                  invoiceValidations.value[globalIndex] = item.validationResult
                }
              })
            }
            
            ElMessage.success(`成功添加 ${newInvoices.length} 张发票`)
            
            // 上传完成后隐藏继续上传界面
            showContinueUpload.value = false
            clearContinueUploadFiles()
            continueUploadReason.value = ''
          }
        } else {
          ElMessage.error(`文件 ${file.name} 上传失败: ${result.message}`)
        }
      } catch (error) {
        ElMessage.error(`文件 ${file.name} 上传失败: ${(error as Error).message}`)
      }
    }
    
  } catch (error) {
    ElMessage.error('上传失败: ' + (error as Error).message)
  } finally {
    continueUploading.value = false
  }
}

// 取消继续上传
const handleCancelContinueUpload = () => {
  showContinueUpload.value = false
  clearContinueUploadFiles()
  continueUploadReason.value = ''
}

// 清空继续上传文件
const clearContinueUploadFiles = () => {
  continueUploadFiles.value = []
  ElMessage.success('已清除本次上传文件')
}

const handleInvoiceDelete = async (index: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这张发票吗？', '删除确认', {
      type: 'warning'
    })
    
    invoiceInfos.value.splice(index, 1)
    delete invoiceValidations.value[index]
    
    // 重新索引校验数据
    const newValidations: Record<number, ValidationResult> = {}
    Object.keys(invoiceValidations.value).forEach(key => {
      const oldIndex = parseInt(key)
      const validation = invoiceValidations.value[oldIndex]
      if (validation) { // 确保validation存在
        if (oldIndex > index) {
          newValidations[oldIndex - 1] = validation
        } else if (oldIndex < index) {
          newValidations[oldIndex] = validation
        }
      }  
    })
    invoiceValidations.value = newValidations
    
    ElMessage.success('删除成功')
  } catch {
    // 用户取消删除
  }
}

const submitReimbursement = async () => {
  // 校验逻辑 - 检查严重错误
  const severeErrorInvoices: number[] = []
  const verificationFailedInvoices: number[] = []
  const specialInvoices: number[] = []
  const limitExceededInvoices: number[] = []
  const dateExpiredInvoices: number[] = []
  
  for (let i = 0; i < invoiceInfos.value.length; i++) {
    const validation = invoiceValidations.value[i]
    
    if (!validation) continue
    
    // 检查真伪验证状态
    const verificationStatus = validators.getVerificationStatus(validation)
    
    if (verificationStatus === 'FAILED') {
      verificationFailedInvoices.push(i + 1)
    }
    
    if (hasSevereValidationError(i)) {
      severeErrorInvoices.push(i + 1)
    }
    
    if (isSpecialInvoice(i)) {
      specialInvoices.push(i + 1)
    }
    
    if (isVerificationLimitExceeded(i)) {
      limitExceededInvoices.push(i + 1)
    }
    
    if (isInvoiceDateExpired(i)) {
      dateExpiredInvoices.push(i + 1)
    }
  }
  
  // 真伪验证失败处理
  if (verificationFailedInvoices.length > 0) {
    ElMessageBox.alert(
      `发票 ${verificationFailedInvoices.join(', ')} 真伪验证失败，请检查发票真实性`,
      '真伪验证失败',
      { type: 'error' }
    )
    return
  }
  
  // 严重错误处理
  if (severeErrorInvoices.length > 0) {
    ElMessageBox.alert(
      `发票 ${severeErrorInvoices.join(', ')} 存在严重问题，请处理后再提交`,
      '存在严重错误',
      { type: 'error' }
    )
    return
  }
  
  // 警告信息提示
  const warningMessages: string[] = []
  
  if (limitExceededInvoices.length > 0) {
    warningMessages.push(`发票 ${limitExceededInvoices.join(', ')} 验证次数超限，将在提交后人工验证`)
  }
  
  if (dateExpiredInvoices.length > 0) {
    warningMessages.push(`发票 ${dateExpiredInvoices.join(', ')} 开票日期超过一年，请在消费事由中说明原因`)
  }
  
  if (specialInvoices.length > 0) {
    warningMessages.push(`发票 ${specialInvoices.join(', ')} 为特殊票据，将在提交后人工验证`)
  }
  
  // 如果有警告信息，提示用户但允许继续提交
  if (warningMessages.length > 0) {
    const userConfirmed = await ElMessageBox.confirm(
      `以下发票存在警告信息：\n\n${warningMessages.join('\n')}\n\n是否继续提交？`,
      '警告信息',
      {
        confirmButtonText: '继续提交',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    if (!userConfirmed) {
      return
    }
  }

  // 提交逻辑
  submitting.value = true
  showProgressModal.value = true
  
  try {
    const reimbursementDate = reimbursementForm.reimbursementDate 
      ? reimbursementForm.reimbursementDate 
      : new Date().toISOString().split('T')[0]

    // 添加调试日志，确认提交前的mediaId值
    console.log('提交前的invoiceInfos mediaId:', invoiceInfos.value.map(inv => inv.mediaId))

    const submitData: ReimbursementSubmitData = {
      invoices: invoiceInfos.value.map(invoice => ({
        ...invoice,
        consumptionReason: invoice.consumptionReason || '',
        mediaId: invoice.mediaId || ''
      })),
      mediaIds: invoiceInfos.value.map(invoice => invoice.mediaId).filter(Boolean).join(','),
      totalAmount: totalAmount.value.replace('¥', ''),
      formType: formType.value,
      formReimbursementReason: reimbursementForm.formReimbursementReason,
      legalEntity: reimbursementForm.legalEntity,
      region: reimbursementForm.region,
      costDepartment: reimbursementForm.costDepartment,
      customerName: reimbursementForm.customerName || '',
      unsignedCustomer: reimbursementForm.unsignedCustomer || '',
      userId: authStore.userInfo?.userid || '',
      userName: authStore.userInfo?.name || '',
      reimbursementDate: reimbursementForm.reimbursementDate as string,
      travelStartDate: reimbursementForm.travelStartDate || '',
      travelStartPeriod: reimbursementForm.travelStartPeriod || '',
      travelEndDate: reimbursementForm.travelEndDate || '',
      travelEndPeriod: reimbursementForm.travelEndPeriod || '',
      travelDays: reimbursementForm.travelDays || '',
      submitTravelSubsidy: reimbursementForm.submitTravelSubsidy // 出差补贴申请单开关状态
    }
    
    // 添加调试日志，确认提交数据中的mediaId
    console.log('提交数据中的mediaId:', submitData.invoices.map(inv => inv.mediaId))
    console.log('提交数据中的mediaIds:', submitData.mediaIds)

    const result = await apiSubmitReimbursement(submitData)
    
    if (result.success) {
      submitSuccess.value = true
      ElMessage.success('报销申请提交成功')
    } else {
      ElMessage.error(result.message || '提交失败')
    }
  } catch (error) {
    ElMessage.error('提交失败: ' + (error as Error).message)
  } finally {
    submitting.value = false
    showProgressModal.value = false
  }
}

const hasSevereValidationError = (index: number): boolean => {
  const validation = invoiceValidations.value[index]
  const invoice = invoiceInfos.value[index]
  
  if (!validation || !invoice) return false
  
  // 使用验证器工具函数
  return validators.hasSevereValidationError(validation, invoice)
}

// 检查发票是否为特殊发票
const isSpecialInvoice = (index: number): boolean => {
  const validation = invoiceValidations.value[index]
  const invoice = invoiceInfos.value[index]
  
  if (!validation || !invoice) return false
  
  return validators.isSpecialInvoice(validation, invoice)
}

const isVerificationLimitExceeded = (index: number): boolean => {
  const validation = invoiceValidations.value[index]
  
  if (!validation) return false
  
  return validators.isVerificationLimitExceeded(validation)
}

// 检查开票日期是否超过一年
const isInvoiceDateExpired = (index: number): boolean => {
  const validation = invoiceValidations.value[index]
  
  if (!validation) return false
  
  return validators.isInvoiceDateExpired(validation)
}

const handleValidationResult = (result: any, index: number) => {
  if (!result || !result.validationResult) return
  
  const validation = result.validationResult
  
  // 检查真伪验证状态
  const verificationStatus = validators.getVerificationStatus(validation)
  
  // 如果真伪验证失败，显示错误信息
  if (verificationStatus === 'FAILED') {
    ElMessage.error(`发票 ${index + 1} 真伪验证失败，请检查发票真实性`)
  }
  
  // 获取验证提示信息
  const messages = validators.getValidationMessages(validation)
  if (messages.length > 0) {
    messages.forEach(message => {
      ElMessage.warning(message)
    })
  }
  
  // 存储验证结果
  invoiceValidations.value[index] = validation
}

const handleSubmitSuccess = () => {
  submitSuccess.value = false
  resetForm()
}

const resetForm = () => {
  currentStep.value = 0
  formType.value = ''
  selectedFiles.value = []
  invoiceInfos.value = []
  invoiceValidations.value = {}
  
  Object.assign(reimbursementForm, {
    formType: '',
    submitter: authStore.userInfo?.name || '',
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
    travelDays: '0',
    submitTravelSubsidy: true // 重置为默认打开状态
  })
}
</script>

<style scoped lang="scss">
.reimbursement-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
  
  // 用户信息和退出按钮
  .user-info-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: #fff;
    padding: 15px 20px;
    border-radius: 8px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    margin-bottom: 20px;
    
    .user-info {
      display: flex;
      align-items: center;
      gap: 12px;
      
      .user-name {
        font-size: 16px;
        font-weight: 500;
        color: #303133;
      }
    }
    
    .logout-btn {
      color: #909399;
      font-size: 14px;
      
      &:hover {
        color: #f56c6c;
      }
    }
  }
  
  // 流程步骤
  .flow-steps {
    background: #fff;
    border-radius: 8px;
    padding: 20px;
    margin-bottom: 20px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    
    .steps-container {
      display: flex;
      justify-content: space-between;
      align-items: center;
      position: relative;
      max-width: 600px;
      margin: 0 auto;
      
      .step-line {
        position: absolute;
        top: 50%;
        left: 50px;
        right: 50px;
        height: 2px;
        background: #e4e7ed;
        transform: translateY(-50%);
        z-index: 1;
        transition: all 0.3s ease;
      }
      
      .step-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        position: relative;
        z-index: 2;
        flex: 1;
        min-width: 0;
        
        .step-icon {
          width: 36px;
          height: 36px;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 14px;
          font-weight: 500;
          margin-bottom: 8px;
          background: #f5f7fa;
          color: #c0c4cc;
          border: 2px solid #e4e7ed;
          transition: all 0.3s ease;
        }
        
        .step-label {
          font-size: 14px;
          color: #909399;
          text-align: center;
          transition: all 0.3s ease;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
          max-width: 100%;
        }
        
        &.active {
          .step-icon {
            background: #409eff;
            border-color: #409eff;
            color: white;
          }
          
          .step-label {
            color: #409eff;
            font-weight: 500;
          }
        }
        
        &.completed {
          .step-icon {
            background: #67c23a;
            border-color: #67c23a;
            color: white;
          }
        }
      }
    }
    
    // 响应式横线长度调整
    @media (max-width: 1200px) {
      .steps-container {
        max-width: 500px;
        
        .step-line {
          left: 40px;
          right: 40px;
        }
      }
    }
    
    @media (max-width: 992px) {
      .steps-container {
        max-width: 450px;
        
        .step-line {
          left: 35px;
          right: 35px;
        }
      }
    }
    
    @media (max-width: 768px) {
      .steps-container {
        max-width: 400px;
        
        .step-line {
          left: 30px;
          right: 30px;
        }
        
        .step-item {
          .step-icon {
            width: 32px;
            height: 32px;
            font-size: 13px;
          }
          
          .step-label {
            font-size: 12px;
          }
        }
      }
    }
    
    @media (max-width: 480px) {
      .steps-container {
        max-width: 350px;
        
        .step-line {
          left: 25px;
          right: 25px;
        }
        
        .step-item {
          .step-label {
            font-size: 11px;
          }
        }
      }
    }
  }
  
  // 选择类型步骤
  .type-select-step {
    background: #fff;
    border-radius: 8px;
    padding: 30px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    
    h2 {
      text-align: center;
      margin-bottom: 30px;
      color: #303133;
      font-size: 24px;
      font-weight: 600;
    }
    
    .type-cards {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 20px;
      margin-bottom: 30px;
      
      .type-card {
        cursor: pointer;
        transition: all 0.3s ease;
        border: 2px solid transparent;
        
        &:hover {
          transform: translateY(-2px);
          box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
        }
        
        &.selected {
          border-color: #409eff;
          background: #f0f7ff;
        }
        
        .type-content {
          text-align: center;
          padding: 20px;
          
          .el-icon {
            color: #409eff;
            margin-bottom: 15px;
          }
          
          h3 {
            margin: 0 0 10px;
            font-size: 18px;
            color: #303133;
          }
          
          p {
            margin: 0;
            color: #909399;
            font-size: 14px;
            line-height: 1.5;
          }
        }
      }
    }
    
    .step-actions {
      text-align: center;
      
      .el-button {
        min-width: 160px;
        height: 44px;
        font-size: 16px;
      }
    }
  }
  
  // 上传文件步骤
  .upload-step {
    background: #fff;
    border-radius: 8px;
    padding: 30px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    
    h2 {
      text-align: center;
      margin-bottom: 30px;
      color: #303133;
      font-size: 24px;
      font-weight: 600;
    }
    
    .reason-section {
      margin-bottom: 30px;
    }
    
    .upload-controls {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin: 20px 0;
      padding: 15px;
      background: #f9fafc;
      border-radius: 6px;
      
      .stats-container {
        display: flex;
        gap: 10px;
        
        .el-tag {
          font-size: 14px;
        }
      }
    }
    
    .step-actions {
      display: flex;
      justify-content: center;
      gap: 15px;
      margin-top: 30px;
      flex-wrap: wrap;
      
      .el-button {
        min-width: 120px;
        height: 40px;
        font-size: 14px;
        flex-shrink: 1;
      }
    }
  }
  
  // 填写信息步骤
  .fill-form-step {
    background: #fff;
    border-radius: 8px;
    padding: 30px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    
    h2 {
      text-align: center;
      margin-bottom: 30px;
      color: #303133;
      font-size: 24px;
      font-weight: 600;
    }
    
    .reimbursement-form {
      .el-form-item {
        margin-bottom: 20px;
        
        :deep(.el-form-item__label) {
          font-weight: 500;
          color: #606266;
          display: block;
          margin-bottom: 8px;
          text-align: left;
          width: 100%;
        }
        
        :deep(.el-form-item__content) {
          margin-left: 0 !important;
          line-height: 1.5;
        }
      }
      
      .el-row {
        margin-bottom: 0;
      }
    }
    
    .invoice-list {
      margin: 30px 0;
      
      .invoice-item {
        background: #f9fafc;
        border-radius: 6px;
        padding: 15px;
        margin-bottom: 15px;
        
        .invoice-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 10px;
          
          .invoice-title {
            font-weight: 500;
            color: #303133;
          }
        }
        
        .invoice-details {
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
          gap: 10px;
          
          .detail-item {
            display: flex;
            flex-direction: column;
            gap: 4px;
            
            .detail-label {
              font-size: 12px;
              color: #909399;
            }
            
            .detail-value {
              font-size: 14px;
              color: #303133;
              font-weight: 500;
            }
          }
        }
      }
    }
    
    .step-actions {
      display: flex;
      justify-content: center;
      gap: 15px;
      margin-top: 30px;
      flex-wrap: wrap;
      
      .el-button {
        min-width: 120px;
        height: 40px;
        font-size: 14px;
        flex-shrink: 1;
      }
    }
  }
  
  // 移动端适配
  @media (max-width: 768px) {
    padding: 0 10px;
    
    .user-info-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;
      padding: 0 20px;
      flex-wrap: nowrap; // 防止换行
      
      .user-info {
        display: flex;
        align-items: center;
        gap: 10px;
        flex-shrink: 0; // 防止压缩
        
        .user-name {
          font-weight: bold;
          font-size: 16px;
        }
      }
      
      .logout-btn {
        flex-shrink: 0; // 防止压缩
        white-space: nowrap; // 防止文字换行
      }

      // 响应式设计
      @media (max-width: 768px) {
        padding: 0 10px;
        
        .user-info {
          gap: 8px;
          
          .user-name {
            font-size: 14px;
          }
        }
        
        .logout-btn {
          font-size: 12px;
        }
      }
      
      @media (max-width: 480px) {
        .user-info {
          gap: 6px;
          
          .user-name {
            font-size: 13px;
          }
          
          .el-tag {
            font-size: 10px;
          }
        }
        
        .logout-btn {
          font-size: 11px;
          padding: 6px 8px;
        }
      }

    }
    
    .type-select-step {
      padding: 20px;
      
      h2 {
        font-size: 20px;
        margin-bottom: 20px;
      }
      
      .type-cards {
        grid-template-columns: 1fr;
        gap: 15px;
        
        .type-card {
          .type-content {
            padding: 15px;
            
            h3 {
              font-size: 16px;
            }
            
            p {
              font-size: 13px;
            }
          }
        }
      }
      
      .step-actions {
        .el-button {
          min-width: 140px;
          height: 40px;
          font-size: 15px;
        }
      }
    }
    
    .upload-step {
      padding: 20px;
      
      h2 {
        font-size: 20px;
        margin-bottom: 20px;
      }
      
      .upload-controls {
        flex-direction: column;
        gap: 15px;
        align-items: stretch;
        
        .stats-container {
          justify-content: center;
          flex-wrap: wrap;
        }
        
        .actions-container {
          text-align: center;
        }
      }
      
      .step-actions {
        gap: 10px;
        
        .el-button {
          min-width: 100px;
          height: 38px;
          font-size: 13px;
        }
      }
    }
    
    .fill-form-step {
      padding: 20px;
      
      h2 {
        font-size: 20px;
        margin-bottom: 20px;
      }
      
  .reimbursement-form {
    :deep(.el-form-item) {
      margin-bottom: 24px;
      
      .el-form-item__label {
        display: block;
        text-align: left;
        margin-bottom: 8px;
        font-weight: 600;
        color: #303133;
        width: 100% !important;
        line-height: 1.4;
        padding-bottom: 0;
      }
      
      .el-form-item__content {
        width: 100%;
        line-height: 1.4;
        
        .form-field-content {
          display: block;
          width: 100%;
        }
        
        .el-input,
        .el-select,
        .el-date-editor,
        .el-textarea {
          width: 100%;
        }
        
        .el-tag {
          display: inline-block;
          width: auto;
          margin-top: 4px;
        }
      }
    }
    
    .el-row {
      margin-bottom: 0;
      
      .el-col {
        margin-bottom: 0;
      }
    }
  }
  
    .step-actions {
      display: flex;
      justify-content: center;
      gap: 20px;
      margin-top: 40px;
      
      .el-button {
        min-width: 120px;
      }
    }
      
      .invoice-list {
        .invoice-item {
          .invoice-details {
            grid-template-columns: 1fr;
          }
        }
      }
      
    }
  }
  
  // 超小屏幕适配
  @media (max-width: 480px) {
    .type-select-step,
    .upload-step,
    .fill-form-step {
      padding: 15px;
      
      h2 {
        font-size: 18px;
      }
    }
  }
}

// 成功对话框
.success-dialog {
  text-align: center;
  
  .success-icon {
    font-size: 64px;
    color: #67c23a;
    margin-bottom: 20px;
  }
  
  .success-title {
    font-size: 24px;
    font-weight: 600;
    color: #303133;
    margin-bottom: 10px;
  }
  
  .success-desc {
    color: #909399;
    margin-bottom: 30px;
  }
}

// 继续上传区域
.continue-upload-section {
  background: #f9fafc;
  border-radius: 6px;
  padding: 20px;
  margin: 20px 0;
  
  .section-title {
    font-size: 16px;
    font-weight: 500;
    margin-bottom: 15px;
    color: #303133;
  }
}

// 继续上传按钮居中
.continue-upload {
  display: flex;
  justify-content: center;
  margin: 30px 0;
  
  .el-button {
    min-width: 160px;
    height: 44px;
    font-size: 16px;
  }
}

// 继续上传操作按钮居中
.continue-upload-actions {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 30px;
  
  .el-button {
    min-width: 120px;
  }
}

// 提交区域
.submit-section {
  background: #f0f9ff;
  border: 1px solid #bae0ff;
  border-radius: 6px;
  padding: 20px;
  margin: 20px 0;
  
  .section-title {
    font-size: 16px;
    font-weight: 500;
    margin-bottom: 15px;
    color: #1890ff;
  }
}

// 金额显示
.amount {
  font-size: 24px;
  font-weight: 600;
  color: #f56c6c;
  text-align: center;
  margin: 20px 0;
}

// 操作按钮组
.action-buttons {
  display: flex;
  gap: 10px;
  justify-content: center;
  margin: 20px 0;
}
</style>