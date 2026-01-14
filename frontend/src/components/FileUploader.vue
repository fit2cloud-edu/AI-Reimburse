<template>
  <div class="file-uploader">
    <!-- 拖拽区域 -->
    <div 
      class="upload-area"
      :class="{ 'dragover': isDragging }"
      @dragover.prevent="handleDragOver"
      @dragleave="handleDragLeave"
      @drop.prevent="handleDrop"
      @click="triggerFileInput"
    >
      <div class="upload-content">
        <el-icon size="40" color="#409eff" class="upload-icon-right">
          <Upload />
        </el-icon>
        <div class="upload-texts">
          <p class="upload-text">{{ uploadText }}</p>
          <p class="upload-hint">支持图片（JPG/PNG）和PDF文件</p>
        </div>
      </div>
      <input 
        ref="fileInputRef"
        type="file"
        multiple
        accept=".jpg,.jpeg,.png,.pdf"
        @change="handleFileSelect"
        style="display: none"
      />
    </div>
    
    <!-- 文件统计信息
    <div v-if="modelValue.length > 0" class="file-stats">
      <el-tag type="info" size="large">总数量: {{ modelValue.length }}</el-tag>
      <el-tag type="success" size="large">有效文件: {{ validFileCount }}</el-tag>
      <el-tag type="warning" size="large">文档: {{ fileStats.documents }}</el-tag>
      <el-tag type="primary" size="large">图片: {{ fileStats.images }}</el-tag>
    </div>

     文件列表 
    <div v-if="modelValue.length > 0" class="file-list">
      <div 
        v-for="(file, index) in modelValue" 
        :key="getFileKey(file, index)"
        class="file-item"
      >
        <div class="file-info">
          <el-icon class="file-icon" :size="24">
            <component :is="getFileIcon(file)" />
          </el-icon>
          <div class="file-details">
            <div class="file-name text-ellipsis" :title="file.name">
              {{ file.name }}
            </div>
            <div class="file-meta">
              <span class="file-size">{{ formatFileSize(file.size) }}</span>
              <span class="file-type"> · {{ getFileTypeLabel(file) }}</span>
            </div>
          </div>
        </div>
        <div class="file-actions">
          <el-button 
            type="danger" 
            size="small"
            @click.stop="removeFile(index)"
          >
            删除
          </el-button>
        </div>
      </div>
    </div> -->
    
    <!-- 操作按钮 -->
    <div v-if="showActions" class="upload-actions">
      <el-button 
        v-if="modelValue.length > 0"
        @click="handleClear"
      >
        <el-icon><Delete /></el-icon>
        清空
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { 
  Upload, 
  Document, 
  Picture, 
  Loading,
  Delete
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import fileUtils from '../utils/fileUtils'

interface Props {
  modelValue: File[]
  uploading?: boolean
  showActions?: boolean
  uploadText?: string
}

interface Emits {
  (e: 'update:modelValue', files: File[]): void
  (e: 'upload'): void
  (e: 'clear'): void
}

const props = withDefaults(defineProps<Props>(), {
  uploading: false,
  showActions: true,
  uploadText: '点击或拖拽文件到此处上传'
})

const emit = defineEmits<Emits>()

const fileInputRef = ref<HTMLInputElement>()
const isDragging = ref(false)

// 计算文件统计
const fileStats = computed(() => {
  const stats = { images: 0, documents: 0 }
  props.modelValue.forEach(file => {
    const type = fileUtils.getFileType(file.name)
    if (type === 'image') stats.images++
    else if (type === 'document') stats.documents++
  })
  return stats
})

const triggerFileInput = () => {
  fileInputRef.value?.click()
}

const handleFileSelect = (event: Event) => {
  const input = event.target as HTMLInputElement
  if (input.files) {
    handleSelectedFiles(Array.from(input.files))
  }
  input.value = ''
}

const handleDragOver = (event: DragEvent) => {
  event.preventDefault()
  isDragging.value = true
}

const handleDragLeave = () => {
  isDragging.value = false
}

const handleDrop = (event: DragEvent) => {
  event.preventDefault()
  isDragging.value = false
  
  if (event.dataTransfer?.files) {
    handleSelectedFiles(Array.from(event.dataTransfer.files))
  }
}

const handleSelectedFiles = (files: File[]) => {
  const validFiles: File[] = []
  const invalidFiles: string[] = []
  const duplicateFiles: string[] = []
  
  files.forEach(file => {
    if (fileUtils.isAllowedFileType(file.name)) {
      // 检查是否重复文件（基于文件名、大小和最后修改时间）
      const isDuplicate = props.modelValue.some(existingFile => 
        existingFile.name === file.name && 
        existingFile.size === file.size && 
        existingFile.lastModified === file.lastModified
      )
      
      if (isDuplicate) {
        duplicateFiles.push(file.name)
      } else {
        validFiles.push(file)
      }
    } else {
      invalidFiles.push(file.name)
    }
  })
  
  // 显示警告信息
  if (invalidFiles.length > 0) {
    ElMessage.warning(`以下文件类型不支持: ${invalidFiles.join(', ')}`)
  }
  
  if (duplicateFiles.length > 0) {
    ElMessage.warning(`以下文件已存在，已自动跳过: ${duplicateFiles.join(', ')}`)
  }
  
  if (validFiles.length > 0) {
    const newFiles = [...props.modelValue, ...validFiles]
    emit('update:modelValue', newFiles)
    ElMessage.success(`已添加 ${validFiles.length} 个有效文件`)
  } else if (files.length > 0 && validFiles.length === 0) {
    ElMessage.info('没有新的有效文件可以添加')
  }
}

// 计算有效文件数量
const validFileCount = computed(() => {
  return props.modelValue.length
})


const removeFile = (index: number) => {
  const newFiles = [...props.modelValue]
  newFiles.splice(index, 1)
  emit('update:modelValue', newFiles)
}

const handleUpload = () => {
  if (props.modelValue.length === 0) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  emit('upload')
}

const handleClear = () => {
  emit('clear')
}

// 工具函数
const getFileKey = (file: File, index: number) => {
  return `${file.name}-${file.size}-${file.lastModified}-${index}`
}

const getFileIcon = (file: File) => {
  const type = fileUtils.getFileType(file.name)
  return type === 'image' ? Picture : Document
}

const getFileTypeLabel = (file: File) => {
  const type = fileUtils.getFileType(file.name)
  return type === 'image' ? '图片' : type === 'document' ? '文档' : '其他'
}

const formatFileSize = fileUtils.formatFileSize
</script>

<style scoped lang="scss">
.file-uploader {
  .upload-area {
    border: 2px dashed #dcdfe6;
    border-radius: 8px;
    padding: 10px 20px; /* 减少上下内边距，降低高度 */
    text-align: center;
    cursor: pointer;
    transition: all 0.3s ease;
    background-color: #fafafa;
    box-sizing: border-box;
    min-height: 20px; /* 设置最小高度，与3行文本输入框高度匹配 */
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    
    &:hover {
      border-color: #409eff;
      background-color: rgba(64, 158, 255, 0.05);
    }
    
    &.dragover {
      border-color: #409eff;
      background-color: rgba(64, 158, 255, 0.1);
    }
    
    .upload-content {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 15px;
      
      .upload-icon-right {
        flex-shrink: 0;
      }
      
      .upload-texts {
        text-align: left;
        
        .upload-text {
          margin-bottom: 4px;
          color: #303133;
          font-size: 16px;
          font-weight: 500;
        }
        
        .upload-hint {
          color: #909399;
          font-size: 14px;
        }
      }
    }

    .upload-icon {
      margin-bottom: 15px;
    }
    
    .upload-text {
      margin-bottom: 6px;
      color: #303133;
      font-size: 16px;
      font-weight: 500;
    }
    
    .upload-hint {
      color: #909399;
      font-size: 14px;
    }
  }
  
  .file-list {
    margin: 20px 0;
    
    .file-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 12px 16px;
      background: white;
      border-radius: 8px;
      margin-bottom: 8px;
      border: 1px solid #ebeef5;
      transition: all 0.3s ease;
      
      &:hover {
        border-color: #409eff;
        box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
      }
      
      .file-info {
        display: flex;
        align-items: center;
        flex: 1;
        min-width: 0;
        
        .file-icon {
          color: #409eff;
          margin-right: 12px;
          flex-shrink: 0;
        }
        
        .file-details {
          flex: 1;
          min-width: 0;
          
          .file-name {
            color: #303133;
            font-size: 14px;
            margin-bottom: 4px;
            font-weight: 500;
          }
          
          .file-meta {
            color: #909399;
            font-size: 12px;
            
            .file-size {
              margin-right: 4px;
            }
          }
        }
      }
      
      .file-actions {
        flex-shrink: 0;
        margin-left: 12px;
      }
    }
  }
  
  .file-stats {
    margin: 20px 0;
    display: flex;
    gap: 10px;
    flex-wrap: wrap;
    
    .el-tag {
      font-weight: 500;
    }
  }
  
  .upload-actions {
    margin-top: 20px;
    display: flex;
    gap: 10px;
  }
}
</style>