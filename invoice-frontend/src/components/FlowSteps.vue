<template>
  <div class="flow-steps">
    <el-steps 
      :active="currentStep" 
      :space="200" 
      finish-status="success"
      align-center
    >
      <el-step
        v-for="(step, index) in steps"
        :key="index"
        :title="step.title"
        :icon="step.icon"
        :status="getStepStatus(index)"
        @click="handleStepClick(index)"
      >
        <template #description>
          <span class="step-description">{{ getStepDescription(index) }}</span>
        </template>
      </el-step>
    </el-steps>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  steps: Array<{
    title: string
    icon?: any
    description?: string
  }>
  currentStep: number
  clickable?: boolean
}

interface Emits {
  (e: 'step-click', index: number): void
}

const props = withDefaults(defineProps<Props>(), {
  clickable: true
})

const emit = defineEmits<Emits>()

const getStepStatus = (index: number) => {
  if (index < props.currentStep) return 'finish'
  if (index === props.currentStep) return 'process'
  return 'wait'
}

const getStepDescription = (index: number) => {
  if (index < props.currentStep) return '已完成'
  if (index === props.currentStep) return '进行中'
  return '未开始'
}

const handleStepClick = (index: number) => {
  if (props.clickable && index < props.currentStep) {
    emit('step-click', index)
  }
}
</script>

<style scoped lang="scss">
.flow-steps {
  margin-bottom: 40px;
  display: flex;
  justify-content: center;
  width: 100%;

  :deep(.el-steps) {
    display: flex;
    justify-content: space-between; // 改为space-between实现等距分布
    width: 100%;
    max-width: 800px; // 设置最大宽度
    min-width: 400px; // 设置最小宽度
    
    &.el-steps--horizontal {
      justify-content: space-between;
    }

    // 移除步骤间的连接线
    .el-step__line {
      display: none;
    }

  }

  :deep(.el-step) {
    cursor: pointer;
    flex-shrink: 0;
    flex: 1; // 每个步骤占据相同空间
    text-align: center;
    min-width: 100px; // 设置最小宽度防止过窄
    
    &.is-process,
    &.is-finish {
      .el-step__head {
        .el-step__icon {
          border-color: #409eff;
          background-color: #409eff;
          color: white;
        }
      }
      
      .el-step__title {
        color: #409eff;
      }
    }
    
    &.is-wait {
      .el-step__head {
        .el-step__icon {
          border-color: #c0c4cc;
          background-color: white;
          color: #c0c4cc;
        }
      }
      
      .el-step__title {
        color: #c0c4cc;
      }
    }
    
    &.is-process {
      .el-step__head {
        .el-step__icon {
          background-color: #409eff;
        }
      }
    }
    
    .el-step__main {
      .el-step__title {
        font-weight: bold;
        font-size: 16px;
      }
      
      .step-description {
        color: #909399;
        font-size: 12px;
      }
    }
  }

// 响应式设计
  @media (max-width: 768px) {
    :deep(.el-steps) {
      max-width: 100%;
      min-width: 300px;
    }
    
    :deep(.el-step) {
      min-width: 80px;
      
      .el-step__main {
        .el-step__title {
          font-size: 14px;
        }
        
        .step-description {
          font-size: 11px;
        }
      }
    }
  }
  
  @media (max-width: 480px) {
    :deep(.el-step) {
      min-width: 60px;
      
      .el-step__main {
        .el-step__title {
          font-size: 12px;
        }
        
        .step-description {
          font-size: 10px;
        }
      }
    }
  }

}
</style>