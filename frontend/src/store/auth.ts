import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, checkSession as apiCheckSession } from '@/api/auth'
import { ElMessage } from 'element-plus'

export interface UserInfo {
  userid: string
  name: string
  sessionKey?: string
  expiresIn?: number
  department?: string
  region?: string
    departmentStructure?: {
    departmentId: string
    departmentName: string
    fullPath: string
    departmentHierarchy: string[]
    region: string
    regionDepartmentId: string
  }
  departmentId?: string
  departmentName?: string
  departmentFullPath?: string
  departmentHierarchy?: string[]
  regionDepartmentId?: string
  loginSource?: string
}

export const useAuthStore = defineStore('auth', () => {
  // 状态
  const isLoggedIn = ref(false)
  const userInfo = ref<UserInfo | null>(null)
  const sessionKey = ref<string>('')
  const baseUrl = ref<string>(import.meta.env.VITE_APP_BASE_URL || '')
  
  // getters
  const userId = computed(() => userInfo.value?.userid || '')
  const userName = computed(() => userInfo.value?.name || '')
  
  // 从本地存储加载
  const loadFromStorage = () => {
    try {
      const stored = localStorage.getItem('auth')
      if (stored) {
        const data = JSON.parse(stored)
        // 检查是否过期（24小时）
        const timestamp = data.timestamp || 0
        const now = Date.now()
        const expiresIn = 30 * 60 * 1000 // 30分钟
        
        if (now - timestamp < expiresIn) {
          // 确保用户信息中的中文字符正确显示
          if (data.userInfo && data.userInfo.name) {
            // 如果name字段包含URL编码字符，进行解码
            try {
              data.userInfo.name = decodeURIComponent(data.userInfo.name)
            } catch (error) {
              console.warn('用户姓名解码失败，使用原始值:', error)
            }
          }
          if (data.userInfo && data.userInfo.departmentName) {
            // 如果departmentName字段包含URL编码字符，进行解码
            try {
              data.userInfo.departmentName = decodeURIComponent(data.userInfo.departmentName)
            } catch (error) {
              console.warn('部门名称解码失败，使用原始值:', error)
            }
          }
          
          userInfo.value = data.userInfo
          sessionKey.value = data.sessionKey
          isLoggedIn.value = true
        } else {
          // 清除过期数据
          localStorage.removeItem('auth')
          ElMessage.info('登录已过期，请重新登录')
        }
      }
    } catch (error) {
      console.error('加载存储的认证信息失败:', error)
    }
  }
  
  // 登录
  const login = async (code: string) => {
    try {
      const response = await apiLogin(code)
      if (response.success && response.data) {
        const {        
          userId: userid, 
          userName: name, 
          sessionKey: sk,
          departmentStructure,
          departmentId,
          departmentName,
          departmentFullPath,
          departmentHierarchy,
          region,
          regionDepartmentId
        } = response.data
        
        userInfo.value = {
          userid,
          name,
          sessionKey: sk,
          departmentStructure,
          departmentId,
          departmentName,
          departmentFullPath,
          departmentHierarchy,
          region,
          regionDepartmentId,
          department: departmentName || departmentStructure?.departmentName
        }
        sessionKey.value = sk || ''
        isLoggedIn.value = true
        
        // 保存到本地存储
        localStorage.setItem('auth', JSON.stringify({
          userInfo: userInfo.value,
          sessionKey: sk,
          timestamp: Date.now()
        }))
        
        ElMessage.success('登录成功')
        return true
      } else {
        ElMessage.error(response.message || '登录失败')
        return false
      }
    } catch (error) {
      ElMessage.error('登录请求失败')
      return false
    }
  }
  
  // 检查session
  const checkSession = async () => {
    if (!sessionKey.value) return false
    
    try {
      const response = await apiCheckSession(sessionKey.value)
      return response.success && response.data === true
    } catch (error) {
      return false
    }
  }
  
  // 退出登录
  const logout = () => {
    userInfo.value = null
    sessionKey.value = ''
    isLoggedIn.value = false
    localStorage.removeItem('auth')
    ElMessage.success('已退出登录')
  }
  
  // 初始化
  loadFromStorage()
  
  return {
    isLoggedIn,
    userInfo,
    sessionKey,
    baseUrl,
    userId,
    userName,
    login,
    checkSession,
    logout,
    loadFromStorage
  }
})