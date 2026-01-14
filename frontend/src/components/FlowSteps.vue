<template>
  <div class="flow-steps">
    <el-steps 
      :active="currentStep" 
      :space="200" 
      finish-status="success"
      align-center
      class="custom-steps"
    >
      <el-step
        v-for="(step, index) in steps"
        :key="index"
        :title="step.title"
        :icon="step.icon"
        :status="getStepStatus(index)"
        @click="handleStepClick(index)"
        :class="`custom-step custom-step-${getStepStatus(index)}`"
      >
        <template #description>
          <span class="step-description">{{ getStepDescription(index) }}</span>
        </template>
      </el-step>
    </el-steps>
  </div>
</template>


<style scoped lang="scss">
.flow-steps {
  margin-bottom: 40px;
  display: flex;
  justify-content: center;
  width: 100%;

  :deep(.custom-steps) {
    display: flex;
    justify-content: space-between;
    width: 100%;
    max-width: 800px;
    min-width: 400px;
    
    &.el-steps--horizontal {
      justify-content: space-between;
    }

    // 移除步骤间的连接线
    .el-step__line {
      display: none;
    }

    .custom-step {
      cursor: pointer;
      flex-shrink: 0;
      flex: 1;
      text-align: center;
      min-width: 100px;
      
      // 清除步骤图标的长方形背景色
      .el-step__head {
        .el-step__icon {
          // 清除所有可能的背景色和边框
          background: none !important;
          background-color: transparent !important;
          border: none !important;
          box-shadow: none !important;
          outline: none !important;
          
          // 清除图标内部的背景色
          .el-step__icon-inner {
            background: none !important;
            background-color: transparent !important;
          }
          
          // 清除SVG图标的背景色
          svg {
            background: none !important;
            background-color: transparent !important;
          }
        }
      }
      
      &.custom-step-wait {
        .el-step__head {
          .el-step__icon {
            border: 2px solid #000000 !important;
            background-color: white !important;
            color: #000000 !important;

            // 确保图标内部也是白色背景
            .el-step__icon-inner {
              background-color: white !important;
            }
          }
        }
        
        .el-step__title {
          color: #000000 !important;
          font-weight: bold !important;
        }
      }
      
      &.custom-step-process {
        .el-step__head {
          .el-step__icon {
            border: 2px solid #409eff !important;
            background-color: #409eff !important;
            color: white !important;

            // 确保图标内部也是蓝色背景
            .el-step__icon-inner {
              background-color: #409eff !important;
            }
          }
        }
        
        .el-step__title {
          color: #409eff !important;
          font-weight: bold !important;
        }
      }
      
      &.custom-step-finish {
        .el-step__head {
          .el-step__icon {
            border: 2px solid #909399 !important;
            background-color: #909399 !important;
            color: white !important;
            
            // 确保图标内部也是灰色背景
            .el-step__icon-inner {
              background-color: #909399 !important;
            }
          }
        }
        
        .el-step__title {
          color: #909399 !important;
          font-weight: bold !important;
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
  }

  // 响应式设计
  @media (max-width: 768px) {
    :deep(.custom-steps) {
      max-width: 100%;
      min-width: 300px;
      
      .custom-step {
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
  }
  
  @media (max-width: 480px) {
    :deep(.custom-steps) {
      .custom-step {
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
}
</style>

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

<!-- <style scoped lang="scss">
.flow-steps {
  margin-bottom: 40px;
  display: flex;
  justify-content: center;
  width: 100%;

  :deep(.el-steps) {
    display: flex;
    justify-content: space-between; // space-between实现等距分布
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
</style> -->