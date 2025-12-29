// 文件工具函数

// 获取文件扩展名
export function getFileExtension(filename: string): string {
  return filename.split('.').pop()?.toLowerCase() || ''
}

// 检查文件类型是否允许
export function isAllowedFileType(filename: string): boolean {
  const extension = getFileExtension(filename)
  const allowedExtensions = ['jpg', 'jpeg', 'png', 'pdf']
  return allowedExtensions.includes(extension)
}

// 获取文件类型（图片/文档）
export function getFileType(filename: string): 'image' | 'document' | 'other' {
  const extension = getFileExtension(filename)
  if (['jpg', 'jpeg', 'png'].includes(extension)) {
    return 'image'
  } else if (['pdf'].includes(extension)) {
    return 'document'
  }
  return 'other'
}

// 格式化文件大小
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 Bytes'
  
  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 生成文件对象URL
export function createFileObjectURL(file: File): string {
  return URL.createObjectURL(file)
}

// 释放文件对象URL
export function revokeFileObjectURL(url: string): void {
  URL.revokeObjectURL(url)
}

// 批量上传文件
export async function uploadFiles(
  files: File[],
  url: string,
  onProgress?: (progress: number) => void,
  formData?: Record<string, any>
): Promise<any[]> {
  const results = []
  
  for (let i = 0; i < files.length; i++) {
    const file = files[i]
    if (!file) continue;

    const formDataObj = new FormData()
    formDataObj.append('file', file)
    
    // 添加额外的表单数据
    if (formData) {
      Object.keys(formData).forEach(key => {
        formDataObj.append(key, formData[key])
      })
    }
    
    try {
      const response = await fetch(url, {
        method: 'POST',
        body: formDataObj
      })
      
      if (!response.ok) {
        throw new Error(`上传失败: ${response.statusText}`)
      }
      
      const data = await response.json()
      results.push(data)
      
      // 更新进度
      if (onProgress) {
        const progress = Math.round(((i + 1) / files.length) * 100)
        onProgress(progress)
      }
    } catch (error) {
      console.error(`文件 ${file.name} 上传失败:`, error)
      throw error
    }
  }
  
  return results
}

// 下载文件
export function downloadFile(
  url: string,
  filename: string
): Promise<void> {
  return fetch(url)
    .then(response => response.blob())
    .then(blob => {
      const link = document.createElement('a')
      link.href = URL.createObjectURL(blob)
      link.download = filename
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      URL.revokeObjectURL(link.href)
    })
}

// 读取文件为DataURL
export function readFileAsDataURL(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

// 读取文件为ArrayBuffer
export function readFileAsArrayBuffer(file: File): Promise<ArrayBuffer> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as ArrayBuffer)
    reader.onerror = reject
    reader.readAsArrayBuffer(file)
  })
}

// 检查文件大小限制
export function checkFileSize(
  file: File,
  maxSizeMB: number = 10
): boolean {
  const maxSizeBytes = maxSizeMB * 1024 * 1024
  return file.size <= maxSizeBytes
}

// 生成唯一的文件名
export function generateUniqueFilename(
  originalFilename: string,
  prefix?: string
): string {
  const timestamp = Date.now()
  const random = Math.random().toString(36).substr(2, 9)
  const extension = getFileExtension(originalFilename)
  
  let filename = `${timestamp}_${random}`
  if (prefix) {
    filename = `${prefix}_${filename}`
  }
  
  return extension ? `${filename}.${extension}` : filename
}

export default {
  getFileExtension,
  isAllowedFileType,
  getFileType,
  formatFileSize,
  createFileObjectURL,
  revokeFileObjectURL,
  uploadFiles,
  downloadFile,
  readFileAsDataURL,
  readFileAsArrayBuffer,
  checkFileSize,
  generateUniqueFilename
}