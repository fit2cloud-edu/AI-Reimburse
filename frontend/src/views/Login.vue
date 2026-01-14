<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-card">
        <!-- 企业微信登录 -->
        <div v-if="showQyLogin" class="qy-login">
          <div class="logo-section">
            <el-icon size="60" color="#409eff">
              <User />
            </el-icon>
            <h1>企业微信登录</h1>
            <p class="welcome-text">欢迎使用AI发票报销</p>
            <div v-if="!isInWechatWork()" class="env-hint">
              <el-tag type="info">当前为非企业微信环境</el-tag>
              <div class="login-method-toggle">
                <el-switch
                  v-model="useJSSDK"
                  active-text="内嵌登录"
                  inactive-text="新窗口登录"
                  @change="toggleLoginMethod"
                />
                <div class="method-description">
                  <p v-if="useJSSDK">• 在当前页面内嵌企业微信登录组件</p>
                  <p v-else>• 在新窗口中打开企业微信登录页面</p>
                </div>
              </div>

            </div>
          </div>
          
          <div class="login-form">
            <div v-if="loginLoading" class="loading-section">
              <el-icon class="loading-icon" size="40">
                <Loading />
              </el-icon>
              <p>正在登录中...</p>
            </div>
            
            <div v-else-if="loginError" class="error-section">
              <el-icon size="40" color="#f56c6c">
                <CircleClose />
              </el-icon>
              <p class="error-message">{{ loginError }}</p>
              <el-button type="primary" @click="retryLogin">
                重试
              </el-button>
            </div>
            


            <!-- 快速登录界面 -->
            <div v-else-if="showQuickLogin" class="quick-login-section">
              <div class="quick-login-header">
                <el-icon size="40" color="#07c160">
                  <User />
                </el-icon>
                <h3>快速登录</h3>
                <p class="quick-login-tip">检测到您在企业微信中，点击快速登录</p>
              </div>
              
              <el-button 
                type="primary" 
                size="large" 
                @click="handleQuickLogin"
                :loading="loginLoading"
                style="width: 100%; margin-bottom: 16px;"
              >
                <el-icon><User /></el-icon>
                企业微信快速登录
              </el-button>
              
              <el-button 
                type="default" 
                size="large" 
                @click="showQuickLogin = false; showQyLogin = true"
                style="width: 100%;"
              >
                切换至标准登录
              </el-button>
            </div>
            
            <!-- 标准登录界面 -->
            <div v-else class="standard-login-section">
              <div class="standard-login-header">
                <el-icon size="40" color="#409eff">
                  <User />
                </el-icon>
                <h3>企业微信登录</h3>
                <p class="standard-login-tip">请选择登录方式</p>
              </div>
              
              <el-button 
                type="primary" 
                size="large" 
                @click="handleStandardLogin"
                :loading="loginLoading"
                style="width: 100%; margin-bottom: 16px;"
              >
                <el-icon><User /></el-icon>
                {{ useJSSDK ? '内嵌登录' : '扫码登录' }}
              </el-button>
              
              <!-- 在企业微信环境中显示快速登录入口 -->
              <div v-if="isInWxWork" class="quick-login-entry">
                <el-divider>或</el-divider>
                <p class="quick-entry-tip">在企业微信中？</p>
                <el-button 
                  type="text" 
                  @click="showQuickLogin = true; showQyLogin = false"
                >
                  切换到快速登录
                </el-button>
              </div>
              
              <!-- <div v-if="!isProduction" class="test-login-section">
                <el-divider>或</el-divider>
                <p class="test-hint">非企微环境也可以使用测试账号登录</p>
                <el-button @click="useTestAccount">
                  使用测试账号
                </el-button>
              </div> -->
              
            </div>
          </div>
        </div>
        
            <!-- 扫码登录界面 -->
            <div v-if="showQrCode" class="qr-code-section">
              <div class="qr-header">
                <el-button type="text" @click="backToLogin" class="back-btn">
                  <el-icon><Back /></el-icon>
                  返回
                </el-button>
                <h3>{{ useJSSDK ? '内嵌登录' : '扫码登录' }}</h3>
              </div>
              
              <div class="qr-content">
                <div class="qr-code-placeholder">
                  <el-icon size="80" color="#409eff">
                    <View />
                  </el-icon>
                  <p>企业微信登录二维码</p>
                </div>
                
                <div class="qr-actions">
                  <el-button 
                    type="primary" 
                    @click="handleQrCodeLogin"
                    :icon="View"
                  >
                    {{ useJSSDK ? '打开内嵌登录' : '在新窗口中打开' }}
                  </el-button>
                  
                  <el-button 
                    @click="copyQrCodeUrl"
                    :icon="DocumentCopy"
                  >
                    复制链接
                  </el-button>
                </div>
                
                <div class="qr-login-hint">
                  <div class="hint-title">登录说明：</div>
                  <div class="hint-content">
                    {{ useJSSDK 
                      ? '点击"打开内嵌登录"将在当前页面嵌入企业微信登录组件' 
                      : '点击"在新窗口中打开"将跳转到企业微信登录页面，扫描二维码完成登录' 
                    }}
                  </div>
                </div>
              </div>
            </div>

        <!-- 测试登录 -->
        <div v-else-if="showTestLogin" class="test-login">
          <div class="logo-section">
            <el-icon size="60" color="#67c23a">
              <User />
            </el-icon>
            <h1>测试登录</h1>
            <p class="welcome-text">测试环境</p>
          </div>
          
          <div class="test-form">
            <el-form 
              ref="testFormRef"
              :model="testForm"
              :rules="testRules"
              label-width="80px"
            >
              <el-form-item label="用户ID" prop="userid">
                <el-input 
                  v-model="testForm.userid" 
                  placeholder="请输入用户ID"
                />
              </el-form-item>
              
              <el-form-item label="姓名" prop="name">
                <el-input 
                  v-model="testForm.name" 
                  placeholder="请输入姓名"
                />
              </el-form-item>
              
              <el-form-item label="部门" prop="department">
                <el-input 
                  v-model="testForm.department" 
                  placeholder="请输入部门"
                />
              </el-form-item>
              
              <el-form-item label="区域" prop="region">
                <el-input 
                  v-model="testForm.region" 
                  placeholder="请输入区域"
                />
              </el-form-item>
            </el-form>
            
            <div class="test-actions">
              <el-button @click="backToQyLogin">
                返回企业微信登录
              </el-button>
              <el-button type="primary" @click="handleTestLogin">
                登录
              </el-button>
            </div>
          </div>
        </div>


      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  User,
  Loading,
  CircleClose,
  View,
  DocumentCopy,
  Back
} from '@element-plus/icons-vue'
import { useAuthStore } from '../store/auth'
import { companyConfig, isProduction } from '../config/company'

const router = useRouter()
const authStore = useAuthStore()

const showQyLogin = ref(true)
const loginLoading = ref(false)
const loginError = ref('')
const showQrCode = ref(false)
const useJSSDK = ref(false) // 控制使用JS-SDK还是链接方式
const qrCodeUrl = ref('')
const showTestLogin = ref(false) // 新增：控制测试登录显示

// 快速登录相关状态
const showQuickLogin = ref(false) // 显示快速登录按钮
const wxWorkSDKLoaded = ref(false) // 企业微信JS-SDK是否加载完成
const isInWxWork = ref(false) // 是否在企业微信环境中

// 测试表单
const testForm = reactive({
  userid: 'CiShiWuJian',
  name: '测试用户',
  department: '智能体开发',
  region: '华南区域',
  departmentId: '7',
  departmentName: '智能体开发',
  departmentFullPath: '华南区域 - 智能体开发',
  departmentHierarchy: ['华南区域', '智能体开发'],
  regionDepartmentId: '4'
})

const testRules = {
  userid: [
    { required: true, message: '请输入用户ID', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' }
  ]
}

// const initQuickLogin = async () => {
//   // 1. 先判断是否在企业微信内置浏览器（UA 检测仍可用）
//   if (isInWechatWork()) {
//     // 企业微信 App 内 → 直接 OAuth2 授权
//     redirectToQyAuth()
//     return
//   }

//   // 2. 否则，在桌面浏览器中尝试初始化快速登录
//   try {
//     await login.init({
//       appId: 'ww71db56bf9d6bec37', // CorpID
//       agentId: '1000008'
//     })

//     // 3. 监听登录状态
//     onCheckWeComLogin((res) => {
//       if (res.isWeComLogin) {
//         // 桌面端已登录企业微信 → 显示快速登录按钮
//         showQuickLogin.value = true
//         showQyLogin.value = false
//       } else {
//         // 未登录 → 显示标准扫码登录
//         showQuickLogin.value = false
//         showQyLogin.value = true
//       }
//     })
//   } catch (error) {
//     console.error('JS-SDK 初始化失败:', error)
//     // 回退到扫码登录
//     showQyLogin.value = true
//   }
// }

// 检查是否在企业微信环境中
const isInWechatWork = () => {
  const ua = navigator.userAgent.toLowerCase()
  const result = ua.includes('wxwork') || ua.includes('micromessenger')
  isInWxWork.value = result // 更新状态
  return result
}

// 替换原有的 isInWechatWork()
const isInWechatWorkApp = () => {
  const ua = navigator.userAgent.toLowerCase()
  // 企业微信 PC 端 UA: wxwork/...
  // 企业微信 手机端 UA: wxwork/... MicroMessenger/...
  return ua.includes('wxwork')
}

// 生成企业微信登录链接（官方推荐格式）
const generateQyLoginUrl = (type: 'link' | 'jssdk' = 'link') => {
  const { corpId, agentId, redirectUri, state } = companyConfig.wechatWork
  
  if (type === 'jssdk') {
    // JS-SDK方式（内嵌登录）
    return `https://login.work.weixin.qq.com/wwlogin/sso/login?login_type=CorpApp&appid=${corpId}&agentid=${agentId}&redirect_uri=${redirectUri}&state=${state}`
  } else {
    // 链接方式（新窗口打开）
    return `https://open.work.weixin.qq.com/wwopen/sso/qrConnect?appid=${corpId}&agentid=${agentId}&redirect_uri=${redirectUri}&state=${state}`
  }
}

// redirect_uri 与后端回调地址一致！
const generateSilentAuthUrl = () => {
  const { corpId, agentId, redirectUri, state } = companyConfig.wechatWork
  const scope = 'snsapi_base' // 静默授权，只获取 userid
  

  // 使用官方推荐的 CorpApp 静默授权链接
  return `https://open.weixin.qq.com/connect/oauth2/authorize?` +
    `appid=${corpId}&` +
    `redirect_uri=${redirectUri}&` +
    `response_type=code&` +
    `scope=${scope}&` +
    `state=${state}&` +
    `agentid=${agentId}` +
    `#wechat_redirect` 
}

// 初始化企业微信登录
const initQyWechatLogin = () => {
  const urlParams = new URLSearchParams(window.location.search)
  
  // 检查是否是后端回调重定向（携带用户信息）
  if (urlParams.has('userid') && urlParams.get('loginStatus') === 'success') {
    console.log('检测到后端回调，自动处理用户信息')
    handleBackendCallback(urlParams)
    return
  }
  
  // 如果用户已经登录，直接跳转到主页
  if (authStore.isLoggedIn) {
    console.log('用户已登录，直接跳转到主页')
    router.push('/')
    return
  }

   // 检查是否是标准的企业微信回调（携带code）
  const code = urlParams.get('code')
  if (code) {
    console.log('检测到企业微信回调code，执行标准登录流程')
    handleQyLogin()
    return
  }
  
  //如果在企业微信 App 内，立即跳转静默授权
  if (isInWechatWorkApp()) {
    console.log('检测到企业微信 App，跳转 oauth2 静默授权...')
    window.location.href = generateSilentAuthUrl()
    return
  }
  
  // 默认显示标准登录界面
  console.log('非企业微信环境，显示标准登录界面')
  showQyLogin.value = true
}

// 处理后端回调（携带用户信息）
const handleBackendCallback = async (urlParams: URLSearchParams) => {
  try {
    loginLoading.value = true
    
    // URL解码函数 - 处理后端传递的URL编码参数
    const decodeURLParam = (param: string | null): string => {
      if (!param) return ''
      try {
        // 使用decodeURIComponent解码URL编码的参数
        return decodeURIComponent(param)
      } catch (error) {
        console.error('URL参数解码失败:', error)
        // 如果解码失败，返回原始参数
        return param
      }
    }

    // 从URL参数中提取用户信息并进行URL解码
    const userInfo = {
      userid: decodeURLParam(urlParams.get('userid')) || '',
      name: decodeURLParam(urlParams.get('name')) || '',
      departmentId: decodeURLParam(urlParams.get('departmentId')) || '',
      departmentName: decodeURLParam(urlParams.get('departmentName')) || '',
      region: decodeURLParam(urlParams.get('region')) || '',
      sessionKey: decodeURLParam(urlParams.get('sessionKey')) || generateSessionKey(),
      loginSource: decodeURLParam(urlParams.get('loginSource')) || 'qywechat'
    }
    
    // 调试日志：显示解码前后的参数对比
    console.log('URL解码前参数:', {
      userid: urlParams.get('userid'),
      name: urlParams.get('name'),
      departmentName: urlParams.get('departmentName'),
      region: urlParams.get('region')
    })
    
    console.log('URL解码后参数:', userInfo)

    // 验证必要的用户信息
    if (!userInfo.userid || !userInfo.name || !userInfo.sessionKey) {
      throw new Error('缺少必要的用户信息参数')
    }

    // 构建完整的部门结构信息（兼容后端传递的格式）
    const departmentStructure = {
      departmentId: userInfo.departmentId,
      departmentName: userInfo.departmentName,
      fullPath: userInfo.departmentName,
      departmentHierarchy: userInfo.region ? [userInfo.region, userInfo.departmentName] : [userInfo.departmentName],
      region: userInfo.region,
      regionDepartmentId: userInfo.departmentId
    }

    // 保存完整的用户信息到store
    authStore.userInfo = {
      ...userInfo,
      departmentStructure,
      departmentFullPath: userInfo.departmentName,
      departmentHierarchy: departmentStructure.departmentHierarchy,
      regionDepartmentId: userInfo.departmentId
    }
    
    authStore.sessionKey = userInfo.sessionKey
    authStore.isLoggedIn = true
    
    // 保存到本地存储
    localStorage.setItem('auth', JSON.stringify({
      userInfo: authStore.userInfo,
      sessionKey: authStore.sessionKey,
      timestamp: Date.now()
    }))
    
    ElMessage.success('登录成功')
    
    // 清除URL参数，跳转到主页面
    const cleanUrl = window.location.pathname
    window.history.replaceState({}, document.title, cleanUrl)
    router.push('/')
    
  } catch (error) {
    loginError.value = '处理回调异常: ' + (error as Error).message
    console.error('后端回调处理失败:', error)
    // 如果后端回调处理失败，回退到标准登录流程
    showQyLogin.value = true
  } finally {
    loginLoading.value = false
  }
}

// 生成session key的辅助函数
const generateSessionKey = () => {
  return 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9)
}

// 处理企业微信登录
const handleQyLogin = async () => {
  loginLoading.value = true
  loginError.value = ''
  
  try {
    // 获取URL参数中的code（企业微信重定向会带上code）
    const urlParams = new URLSearchParams(window.location.search)
    let code = urlParams.get('code')

    if (!code) {
      // 如果没有code，检查是否在企业微信环境中
      if (isInWechatWork()) {
        // 在企业微信环境中，重定向到授权页面
        console.log('企业微信环境中，自动重定向到授权页面')
        redirectToQyAuth()
        return
      } else {
        // 非企业微信环境，显示扫码登录界面
        console.log('非企业微信环境，显示扫码登录')
        showQrCode.value = true
        qrCodeUrl.value = generateQyLoginUrl('link')
        return
      }
    }

    // 调用后端登录接口
    const success = await authStore.login(code)
    
    if (success) {
      ElMessage.success('登录成功')
      // 清除URL中的code参数
      const cleanUrl = window.location.pathname
      window.history.replaceState({}, document.title, cleanUrl)
      // 跳转到报销页面
      router.push('/')
    } else {
      loginError.value = '登录失败，请重试'
    }
  } catch (error) {
    loginError.value = '登录异常: ' + (error as Error).message
  } finally {
    loginLoading.value = false
  }
}

// 复制扫码链接
const copyQrCodeUrl = async () => {
  try {
    await navigator.clipboard.writeText(qrCodeUrl.value)
    ElMessage.success('链接已复制到剪贴板')
  } catch (error) {
    ElMessage.error('复制失败，请手动复制链接')
  }
}

// 企业微信JS-SDK配置
const initWxWorkSDK = () => {
  return new Promise((resolve, reject) => {
    // 检查是否已经加载过
    if ((window as any).wx) {
      wxWorkSDKLoaded.value = true
      resolve(true)
      return
    }
    
    // 动态加载企业微信JS-SDK
    const script = document.createElement('script')
    script.src = 'https://open.work.weixin.qq.com/wwopen/js/jwxwork-1.0.0.js'
    script.onload = () => {
      wxWorkSDKLoaded.value = true
      console.log('企业微信JS-SDK加载完成')
      resolve(true)
    }
    script.onerror = () => {
      console.error('企业微信JS-SDK加载失败')
      reject(new Error('JS-SDK加载失败'))
    }
    document.head.appendChild(script)
  })
}

// 检测企业微信环境并显示快速登录
const checkWxWorkEnvironment = async () => {
  if (!isInWechatWork()) {
    // 非企业微信环境，显示标准登录
    showQyLogin.value = true
    return
  }
  
  try {
    // 在企业微信环境中，加载JS-SDK
    await initWxWorkSDK()
    
    // 使用JS-SDK检测上下文
    const wx = (window as any).wx
    if (wx) {
      wx.invoke('getContext', {}, (res: { err_msg: string }) => {
        if (res.err_msg === 'getContext:ok') {
          // 在企业微信中，显示快速登录
          showQuickLogin.value = true
          showQyLogin.value = false
          console.log('检测到企业微信环境，显示快速登录')
        } else {
          // 检测失败，显示标准登录
          showQyLogin.value = true
        }
      })
    }
  } catch (error) {
    console.error('企业微信环境检测失败:', error)
    // 检测失败，显示标准登录
    showQyLogin.value = true
  }
}

// 快速登录处理
const handleQuickLogin = () => {
  // 在企业微信环境中，直接跳转到授权页面
  const authUrl = generateQyLoginUrl('jssdk')
  window.location.href = authUrl
}

// 标准扫码登录处理
const handleStandardLogin = () => {
  console.log('标准扫码登录')
  if (useJSSDK.value) {
    handleJSSDKLogin()
  } else {
    handleLinkLogin()
  }
}

// 处理JS-SDK登录（内嵌登录）
const handleJSSDKLogin = () => {
  console.log('使用JS-SDK方式登录')
  // 如果已经在企业微信环境中，直接使用标准登录流程
  if (isInWechatWork()) {
    handleQyLogin()
    return
  }
  
  // 非企业微信环境，使用JS-SDK方式
  showQrCode.value = true
  qrCodeUrl.value = generateQyLoginUrl('jssdk')
  ElMessage.info('请在企业微信中扫描二维码登录')
}

// 处理链接登录（新窗口登录）
const handleLinkLogin = () => {
  console.log('使用链接方式登录')
  // 如果已经在企业微信环境中，直接使用标准登录流程
  if (isInWechatWork()) {
    handleQyLogin()
    return
  }
  
  // 非企业微信环境，使用链接方式
  showQrCode.value = true
  qrCodeUrl.value = generateQyLoginUrl('link')
  ElMessage.info('请在新窗口中扫描二维码登录')
}

// 处理扫码登录
const handleQrCodeLogin = () => {
  window.open(qrCodeUrl.value, '_blank')
}

// 返回登录选择
const backToLogin = () => {
  showQrCode.value = false
  loginError.value = ''
}

// 返回企业微信登录
const backToQyLogin = () => {
  showTestLogin.value = false
  showQyLogin.value = true
}

// 切换登录方式
const toggleLoginMethod = () => {
  useJSSDK.value = !useJSSDK.value
}

// 重定向到企业微信授权页面
const redirectToQyAuth = () => {
  window.location.href = generateQyLoginUrl('link')
}

// 重试登录
const retryLogin = () => {
  loginError.value = ''
  handleQyLogin()
}

// 使用测试账号
const useTestAccount = () => {
  if (isProduction) {
    ElMessage.warning('生产环境不支持测试账号登录')
    return
  }
  showTestLogin.value = true
  showQyLogin.value = false
}

// 处理测试登录
const handleTestLogin = () => {
  // 设置测试用户信息
  authStore.userInfo = {
    userid: testForm.userid,
    name: testForm.name,
    department: testForm.department,
    region: testForm.region,
    departmentStructure: {
      departmentId: testForm.departmentId,
      departmentName: testForm.departmentName,
      fullPath: testForm.departmentFullPath,
      departmentHierarchy: testForm.departmentHierarchy,
      region: testForm.region,
      regionDepartmentId: testForm.regionDepartmentId
    },
    departmentId: testForm.departmentId,
    departmentName: testForm.departmentName,
    departmentFullPath: testForm.departmentFullPath,
    departmentHierarchy: testForm.departmentHierarchy,
    regionDepartmentId: testForm.regionDepartmentId
  }
  authStore.isLoggedIn = true
  
  // 保存到本地存储
  localStorage.setItem('auth', JSON.stringify({
    userInfo: authStore.userInfo,
    sessionKey: 'test_session_key',
    timestamp: Date.now()
  }))
  
  ElMessage.success('测试登录成功')
  router.push('/')
}

// 组件挂载时初始化
onMounted(() => {
  // 初始化企业微信登录检测
  initQyWechatLogin()
})

</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  box-sizing: border-box;
  
  @media (max-width: 768px) {
    padding: 10px;
    align-items: flex-start;
    min-height: 100vh;
  }
}

.login-container {
  width: 100%;
  max-width: 400px;
  margin: 0 auto;
  
  @media (max-width: 768px) {
    max-width: 100%;
    margin-top: 20px;
  }
}

.login-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
  overflow: hidden;
  padding: 40px;
  
  @media (max-width: 768px) {
    padding: 24px 20px;
    border-radius: 8px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }
}

.logo-section {
  text-align: center;
  margin-bottom: 30px;
  
  h1 {
    font-size: 24px;
    margin: 16px 0 8px;
    color: #303133;
    
    @media (max-width: 768px) {
      font-size: 20px;
      margin: 12px 0 6px;
    }
  }
  
  .welcome-text {
    color: #909399;
    font-size: 14px;
    margin-bottom: 20px;
    
    @media (max-width: 768px) {
      font-size: 13px;
      margin-bottom: 16px;
    }
  }
}

.login-form {
  .el-button {
    width: 100%;
    height: 44px;
    font-size: 16px;
    
    @media (max-width: 768px) {
      height: 40px;
      font-size: 14px;
    }
  }
}

.env-hint {
  margin: 20px 0;
  
  .login-method-toggle {
    margin-top: 15px;
    
    .method-description {
      margin-top: 10px;
      font-size: 12px;
      color: #909399;
      
      p {
        margin: 4px 0;
      }
    }
  }
}

.quick-login-section,
.standard-login-section {
  .quick-login-header,
  .standard-login-header {
    text-align: center;
    margin-bottom: 30px;
    
    h3 {
      font-size: 18px;
      margin: 12px 0 8px;
      color: #303133;
      
      @media (max-width: 768px) {
        font-size: 16px;
      }
    }
    
    .quick-login-tip,
    .standard-login-tip {
      color: #909399;
      font-size: 14px;
      
      @media (max-width: 768px) {
        font-size: 13px;
      }
    }
  }
}

.qr-code-section {
  padding: 30px;
  
  @media (max-width: 768px) {
    padding: 20px 16px;
  }
  
  .qr-header {
    display: flex;
    align-items: center;
    margin-bottom: 20px;
    
    .back-btn {
      margin-right: 10px;
    }
    
    h3 {
      flex: 1;
      text-align: center;
      margin: 0;
      font-size: 18px;
      
      @media (max-width: 768px) {
        font-size: 16px;
      }
    }
  }
  
  .qr-content {
    text-align: center;
    
    .qr-actions {
      display: flex;
      gap: 10px;
      margin-bottom: 20px;
      
      @media (max-width: 768px) {
        flex-direction: column;
        gap: 8px;
      }
      
      .el-button {
        flex: 1;
        
        @media (max-width: 768px) {
          width: 100%;
        }
      }
    }
  }
}

.test-form {
  .el-form-item {
    margin-bottom: 20px;
    
    @media (max-width: 768px) {
      margin-bottom: 16px;
    }
  }
  
  .test-actions {
    display: flex;
    gap: 10px;
    
    @media (max-width: 768px) {
      flex-direction: column;
    }
    
    .el-button {
      flex: 1;
    }
  }
}

.loading-section,
.error-section {
  text-align: center;
  padding: 30px 0;
  
  p {
    margin: 16px 0;
    font-size: 16px;
    
    @media (max-width: 768px) {
      font-size: 14px;
      margin: 12px 0;
    }
  }
}

.error-message {
  color: #f56c6c;
  font-size: 14px;
  
  @media (max-width: 768px) {
    font-size: 13px;
  }
}

.login-footer {
  text-align: center;
  margin-top: 30px;
  color: rgba(255, 255, 255, 0.8);
  
  @media (max-width: 768px) {
    margin-top: 20px;
  }
  
  p {
    margin: 4px 0;
    font-size: 12px;
  }
}

:deep(.el-card) {
  @media (max-width: 768px) {
    margin-bottom: 12px;
  }
}

:deep(.el-form-item__label) {
  @media (max-width: 768px) {
    font-size: 14px;
  }
}

:deep(.el-input__inner),
:deep(.el-textarea__inner) {
  @media (max-width: 768px) {
    font-size: 16px;
  }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.qr-login-hint {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px;
  margin-top: 15px;
  border-left: 4px solid #409eff;
  
  .hint-title {
    font-weight: 600;
    color: #303133;
    margin-bottom: 5px;
  }
  
  .hint-content {
    color: #606266;
    font-size: 13px;
    line-height: 1.4;
  }
}
</style>