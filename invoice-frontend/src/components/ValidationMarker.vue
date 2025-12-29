<template>
  <div 
    class="validation-marker"
    :class="[
      markerClass,
      sizeClass,
      { 'clickable': clickable }
    ]"
    :title="tooltip"
    @click="handleClick"
  >
    {{ markerText }}
  </div>
</template>

<script lang="ts">
import { defineComponent, computed } from 'vue'
import type { ValidationResult } from '../store/reimbursement'

interface Props {
  validation?: ValidationResult
  invoiceIndex?: number
  size?: 'small' | 'medium' | 'large'
  clickable?: boolean
}

export default defineComponent({
  name: 'ValidationMarker',
  
  props: {
    validation: {
      type: Object as () => ValidationResult | undefined,
      default: undefined
    },
    invoiceIndex: {
      type: Number,
      default: undefined
    },
    size: {
      type: String as () => 'small' | 'medium' | 'large',
      default: 'medium'
    },
    clickable: {
      type: Boolean,
      default: false
    }
  },
  
  emits: ['click'],
  
  setup(props: Props, { emit }) {
    // 计算标记类型
    const markerType = computed(() => {
      if (!props.validation) return ''
      const violations = props.validation.violations || []

      // 检查严重错误：ERROR级别的违规
      const hasSevereError = violations.some((v: any) => 
        v.severity === 'ERROR'
      )
      
      if (hasSevereError) {
        return 'error'
      }
      
      // 检查是否为特殊发票
      const isSpecialInvoice = violations.some((v: any) => 
        (v.field === 'invoice_verification' && v.message?.includes('发票号码不正确')) ||
        (v.field === 'invoice_type' && v.message?.includes('特殊票据')) ||
        (v.message?.includes('特殊票据'))
      )
      
      if (isSpecialInvoice) return 'special'
      
      // 检查警告
      const hasWarning = violations.some((v: any) => 
        v.severity === 'WARNING'
      )
      
      if (hasWarning) return 'warning'
      
      return ''
    })

    // 计算标记文本
    const markerText = computed(() => {
      switch (markerType.value) {
        case 'error':
          return '!'
        case 'warning':
          return '!'
        case 'special':
          return 'i'
        default:
          return ''
      }
    })

    // 计算样式类
    const markerClass = computed(() => {
      switch (markerType.value) {
        case 'error':
          return 'error-marker'
        case 'warning':
          return 'warning-marker'
        case 'special':
          return 'special-marker'
        default:
          return ''
      }
    })

    const sizeClass = computed(() => `size-${props.size}`)

    // 计算提示文本
    const tooltip = computed(() => {
      if (!props.validation) return ''
      
      const violations = props.validation.violations || []
      const messages = violations.map((v: any) => v.message).filter(Boolean)
      
      if (messages.length > 0) {
        return messages.join('\n')
      }
      
      switch (markerType.value) {
        case 'error':
          return '发票存在严重问题，禁止提交'
        case 'warning':
          return '发票存在问题，请写清楚原因'
        case 'special':
          return '特殊发票，需人工审核'
        default:
          return ''
      }
    })

    const handleClick = () => {
      if (props.clickable) {
        emit('click')
      }
    }

    return {
      markerType,
      markerText,
      markerClass,
      sizeClass,
      tooltip,
      handleClick
    }
  }
})
</script>

<style scoped lang="scss">
.validation-marker {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-weight: bold;
  user-select: none;
  transition: all 0.2s ease;
  margin-left: 6px;
  
  &.clickable {
    cursor: pointer;
    
    &:hover {
      transform: scale(1.1);
    }
  }
  
  // 类型样式
  &.error-marker {
    background-color: #f56c6c;
    color: white;
    border: 2px solid #f56c6c;
    box-shadow: 0 1px 3px rgba(245, 108, 108, 0.3);
  }
  
  &.warning-marker {
    background-color: #e6a23c;
    color: white;
    border: 2px solid #e6a23c;
    box-shadow: 0 1px 3px rgba(230, 162, 60, 0.3);
  }
  
  &.special-marker {
    background-color: #409eff;
    color: white;
    border: 2px solid #409eff;
    box-shadow: 0 1px 3px rgba(64, 158, 255, 0.3);
  }
  
  // 尺寸样式
  &.size-small {
    width: 18px;
    height: 18px;
    font-size: 12px;
    font-weight: 900;
  }
  
  &.size-medium {
    width: 22px;
    height: 22px;
    font-size: 14px;
    font-weight: 900;
  }
  
  &.size-large {
    width: 26px;
    height: 26px;
    font-size: 16px;
    font-weight: 900;
  }
}
</style>