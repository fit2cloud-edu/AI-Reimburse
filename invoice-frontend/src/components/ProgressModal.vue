<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="500px"
    :show-close="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <div class="progress-modal">
      <!-- 进度条 -->
      <div class="progress-bar">
        <el-progress
          :percentage="progressPercentage"
          :status="progressStatus"
          :stroke-width="8"
          :show-text="false"
        />
        <div class="progress-text">
          {{ progress.currentStep }}/{{ progress.totalSteps }}
        </div>
      </div>
      
      <!-- 步骤列表 -->
      <div class="steps-list">
        <div 
          v-for="(step, index) in progress.steps" 
          :key="index"
          class="step-item"
          :class="{ 'completed': step.completed, 'current': index === progress.currentStep }"
        >
          <div class="step-icon">
            <el-icon v-if="step.completed">
              <CircleCheck />
            </el-icon>
            <div v-else class="step-number">
              {{ index + 1 }}
            </div>
          </div>
          <div class="step-content">
            <div class="step-name">{{ step.name }}</div>
            <div v-if="index === progress.currentStep" class="step-message">
              {{ progress.message }}
            </div>
          </div>
        </div>
      </div>
      
      <!-- 详细消息 -->
      <div v-if="progress.message" class="detail-message">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ progress.message }}</span>
      </div>
    </div>
    
    <template #footer>
      <div class="modal-footer">
        <div class="time-info">
          已耗时: {{ elapsedTime }}
        </div>
        <el-button 
          v-if="showCancel"
          @click="handleCancel"
          :disabled="!cancellable"
        >
          取消
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { CircleCheck, InfoFilled } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { watch } from 'vue'

interface Props {
  modelValue: boolean
  progress: {
    currentStep: number
    totalSteps: number
    message: string
    steps: Array<{
      name: string
      completed: boolean
    }>
  }
  title?: string
  showCancel?: boolean
  cancellable?: boolean
}

interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'cancel'): void
}

const props = withDefaults(defineProps<Props>(), {
  title: '处理中...',
  showCancel: true,
  cancellable: true
})

const emit = defineEmits<Emits>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:visible', value)
})

const startTime = ref(Date.now())
const elapsedTime = ref('0秒')

// 计算进度百分比
const progressPercentage = computed(() => {
  if (props.progress.totalSteps === 0) return 0
  return Math.round((props.progress.currentStep / props.progress.totalSteps) * 100)
})

// 进度状态
const progressStatus = computed(() => {
  const percentage = progressPercentage.value
  if (percentage < 30) return 'exception'
  if (percentage < 70) return 'warning'
  return 'success'
})

// 更新时间
const updateElapsedTime = () => {
  const elapsed = Date.now() - startTime.value
  const seconds = Math.floor(elapsed / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  
  if (hours > 0) {
    elapsedTime.value = `${hours}小时${minutes % 60}分钟`
  } else if (minutes > 0) {
    elapsedTime.value = `${minutes}分钟${seconds % 60}秒`
  } else {
    elapsedTime.value = `${seconds}秒`
  }
}

// 定时器
let timer: number

onMounted(() => {
  if (props.modelValue) {
    startTime.value = Date.now()
    timer = window.setInterval(updateElapsedTime, 1000)
  }
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
  }
})

// 监听visible变化
watch(() => props.modelValue, (newValue) => {
  if (newValue) {
    startTime.value = Date.now()
    timer = window.setInterval(updateElapsedTime, 1000)
  } else if (timer) {
    clearInterval(timer)
  }
})

// 取消处理
const handleCancel = async () => {
  if (!props.cancellable) return
  
  try {
    await ElMessageBox.confirm('确定要取消当前操作吗？', '取消确认', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '继续'
    })
    
    emit('cancel')
    visible.value = false
  } catch {
    // 用户选择继续
  }
}
</script>

<style scoped lang="scss">
.progress-modal {
  .progress-bar {
    position: relative;
    margin-bottom: 30px;
    
    .progress-text {
      position: absolute;
      right: 0;
      top: -24px;
      color: #909399;
      font-size: 12px;
    }
  }
  
  .steps-list {
    margin-bottom: 20px;
    
    .step-item {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      margin-bottom: 16px;
      
      &.completed {
        .step-icon {
          background-color: #67c23a;
          color: white;
          
          .el-icon {
            font-size: 14px;
          }
        }
        
        .step-content {
          .step-name {
            color: #67c23a;
          }
        }
      }
      
      &.current {
        .step-icon {
          background-color: #409eff;
          color: white;
        }
        
        .step-content {
          .step-name {
            color: #409eff;
            font-weight: 600;
          }
        }
      }
      
      .step-icon {
        width: 24px;
        height: 24px;
        border-radius: 50%;
        background-color: #ebeef5;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        
        .step-number {
          font-size: 12px;
          font-weight: 600;
          color: #909399;
        }
      }
      
      .step-content {
        flex: 1;
        
        .step-name {
          color: #303133;
          font-size: 14px;
          margin-bottom: 4px;
        }
        
        .step-message {
          color: #909399;
          font-size: 12px;
        }
      }
    }
  }
  
  .detail-message {
    background-color: #f0f9eb;
    border-radius: 4px;
    padding: 12px;
    display: flex;
    align-items: center;
    gap: 8px;
    color: #67c23a;
    font-size: 13px;
    
    .el-icon {
      font-size: 16px;
      flex-shrink: 0;
    }
  }
}

.modal-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  
  .time-info {
    color: #909399;
    font-size: 12px;
  }
}
</style>