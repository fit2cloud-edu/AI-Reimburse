import { ElMessage, ElMessageBox, ElLoading, ElNotification } from 'element-plus'

// 模拟微信小程序API的前端实现
export const wx = {
  // 消息提示
  showToast(options: {
    title: string
    icon?: 'success' | 'error' | 'loading' | 'none'
    duration?: number
    image?: string
    mask?: boolean
  }) {
    const { title, icon = 'none', duration = 2000 } = options
    
    switch (icon) {
      case 'success':
        ElMessage.success({
          message: title,
          duration
        })
        break
      case 'error':
        ElMessage.error({
          message: title,
          duration
        })
        break
      case 'loading':
        ElMessage({
          message: title,
          type: 'info',
          duration,
          showClose: true
        })
        break
      case 'none':
      default:
        ElMessage({
          message: title,
          duration
        })
        break
    }
  },
  
  // 模态对话框
  showModal(options: {
    title?: string
    content: string
    showCancel?: boolean
    cancelText?: string
    cancelColor?: string
    confirmText?: string
    confirmColor?: string
    success?: (res: { confirm: boolean; cancel: boolean }) => void
    fail?: (err: any) => void
    complete?: () => void
  }) {
    const {
      title = '提示',
      content,
      showCancel = true,
      cancelText = '取消',
      confirmText = '确定'
    } = options
    
    ElMessageBox.confirm(content, title, {
      confirmButtonText: confirmText,
      cancelButtonText: showCancel ? cancelText : undefined,
      type: 'warning',
      showCancelButton: showCancel
    })
      .then(() => {
        options.success?.({ confirm: true, cancel: false })
      })
      .catch(() => {
        options.success?.({ confirm: false, cancel: true })
      })
      .finally(() => {
        options.complete?.()
      })
  },
  
  // 加载提示
  showLoading(options: {
    title?: string
    mask?: boolean
    success?: () => void
    fail?: (err: any) => void
    complete?: () => void
  }) {
    const { title = '加载中...', mask = true } = options
    
    const loadingInstance = ElLoading.service({
      lock: mask,
      text: title,
      background: 'rgba(0, 0, 0, 0.7)'
    })
    
    options.success?.()
    options.complete?.()
    
    return {
      hide: () => loadingInstance.close()
    }
  },
  
  hideLoading() {
    const loadingInstance = ElLoading.service()
    loadingInstance.close()
  },
  
  // 显示操作菜单
  showActionSheet(options: {
    itemList: string[]
    itemColor?: string
    success?: (res: { tapIndex: number }) => void
    fail?: (err: any) => void
    complete?: () => void
  }) {
    // Element Plus没有直接对应的组件，使用Notification模拟
    ElNotification({
      title: '请选择操作',
      message: options.itemList.map((item, index) => 
        `<div style="padding: 8px; cursor: pointer;" onclick="window.dispatchEvent(new CustomEvent('action-sheet-select', { detail: ${index} }))">
          ${item}
        </div>`
      ).join(''),
      duration: 0,
      dangerouslyUseHTMLString: true
    })
    
    // 监听选择事件
    const handler = (event: CustomEvent) => {
      options.success?.({ tapIndex: event.detail })
      window.removeEventListener('action-sheet-select', handler as any)
    }
    
    window.addEventListener('action-sheet-select', handler as any)
  },
  
  // 本地存储
  getStorageSync(key: string): any {
    try {
      const value = localStorage.getItem(key)
      return value ? JSON.parse(value) : null
    } catch (error) {
      console.error('获取本地存储失败:', error)
      return null
    }
  },
  
  setStorageSync(key: string, value: any): void {
    try {
      localStorage.setItem(key, JSON.stringify(value))
    } catch (error) {
      console.error('设置本地存储失败:', error)
    }
  },
  
  removeStorageSync(key: string): void {
    try {
      localStorage.removeItem(key)
    } catch (error) {
      console.error('删除本地存储失败:', error)
    }
  },
  
  clearStorageSync(): void {
    try {
      localStorage.clear()
    } catch (error) {
      console.error('清空本地存储失败:', error)
    }
  },
  
  // 获取系统信息
  getSystemInfoSync(): any {
    return {
      SDKVersion: 'web',
      platform: 'web',
      model: 'browser',
      system: navigator.userAgent,
      version: '1.0.0',
      windowWidth: window.innerWidth,
      windowHeight: window.innerHeight,
      pixelRatio: window.devicePixelRatio || 1,
      language: navigator.language
    }
  },
  
  // 网络请求
  request(options: {
    url: string
    method?: string
    data?: any
    header?: Record<string, string>
    success?: (res: any) => void
    fail?: (err: any) => void
    complete?: () => void
  }) {
    const { url, method = 'GET', data, header = {} } = options
    
    fetch(url, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...header
      },
      body: method !== 'GET' ? JSON.stringify(data) : undefined
    })
      .then(response => response.json())
      .then(data => {
        options.success?.(data)
      })
      .catch(error => {
        options.fail?.(error)
      })
      .finally(() => {
        options.complete?.()
      })
  },
  
  // 上传文件
  uploadFile(options: {
    url: string
    filePath: string
    name: string
    formData?: Record<string, any>
    header?: Record<string, string>
    success?: (res: any) => void
    fail?: (err: any) => void
    complete?: () => void
  }) {
    const { url, filePath, name, formData = {} } = options
    
    // 模拟文件上传
    console.log('模拟文件上传:', { url, filePath, name, formData })
    
    setTimeout(() => {
      options.success?.({
        data: JSON.stringify({
          success: true,
          message: '上传成功'
        })
      })
      options.complete?.()
    }, 1000)
  },
  
  // 选择图片
  chooseImage(options: {
    count?: number
    sizeType?: ('original' | 'compressed')[]
    sourceType?: ('album' | 'camera')[]
    success?: (res: { tempFilePaths: string[] }) => void
    fail?: (err: any) => void
    complete?: () => void
  }) {
    // 创建文件输入框
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/*'
    input.multiple = options.count !== 1
    
    input.onchange = (event) => {
      const files = (event.target as HTMLInputElement).files
      if (files && files.length > 0) {
        const tempFilePaths: string[] = []
        Array.from(files).forEach(file => {
          tempFilePaths.push(URL.createObjectURL(file))
        })
        options.success?.({ tempFilePaths })
      } else {
        options.fail?.({ errMsg: '用户取消选择' })
      }
      options.complete?.()
    }
    
    input.click()
  },
  
  // 下载文件
  downloadFile(options: {
    url: string
    header?: Record<string, string>
    success?: (res: { tempFilePath: string }) => void
    fail?: (err: any) => void
    complete?: () => void
  }) {
    fetch(options.url)
      .then(response => response.blob())
      .then(blob => {
        const url = URL.createObjectURL(blob)
        options.success?.({ tempFilePath: url })
      })
      .catch(error => {
        options.fail?.(error)
      })
      .finally(() => {
        options.complete?.()
      })
  }
}

// 企业微信特定API（模拟）
export const qy = {
  login: wx.request,
  checkSession: wx.request,
  chooseMessageFile: wx.chooseImage,
  wedriveSelectFileForDownload: (options: any) => {
    // 模拟微盘选择
    wx.showActionSheet({
      itemList: ['选择文件1.pdf', '选择文件2.pdf', '选择文件3.jpg'],
      success: (res: any) => {
        options.success?.({
          result: {
            selectedTickets: [`ticket_${res.tapIndex + 1}`]
          }
        })
      }
    })
  }
}

export default wx