<template>
  <div id="app">
    <el-container>
      <el-header class="app-header">
        <div class="header-content">
          <div class="logo">
            <el-icon><Money /></el-icon>
            <span>AI发票报销</span>
          </div>
          <div class="user-dropdown-container" v-if="authStore.isLoggedIn">
            <!-- 简化触发器，避免嵌套div导致事件冒泡问题 -->
            <div class="user-trigger" @click.stop="toggleUserDropdown">
              <div class="user-info">
                <el-avatar :size="32" :src="userAvatar">
                  {{ authStore.userInfo?.name?.charAt(0) }}
                </el-avatar>
                <span class="username">{{ authStore.userInfo?.name }}</span>
                <el-icon class="dropdown-icon" :class="{ 'rotate': showUserDropdown }">
                  <ArrowDown />
                </el-icon>
              </div>
            </div>
            <transition name="el-zoom-in-top">
              <div v-show="showUserDropdown" class="user-dropdown-menu" v-click-outside="closeUserDropdown">
                <div class="dropdown-item user-details">
                  <el-avatar :size="40" :src="userAvatar">
                    {{ authStore.userInfo?.name?.charAt(0) }}
                  </el-avatar>
                  <div class="user-details-info">
                    <div class="user-name">{{ authStore.userInfo?.name }}</div>
                    <div class="user-department">{{ authStore.userInfo?.department }}</div>
                  </div>
                </div>
                <el-divider />
                <div class="dropdown-item logout-item" @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>
                  <span>退出登录</span>
                </div>
              </div>
            </transition>
          </div>
        </div>
      </el-header>
      
      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
      
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from './store/auth'
import { Money, ArrowDown, SwitchButton } from '@element-plus/icons-vue'
import { companyConfig } from './config/company'

// 点击外部关闭下拉菜单的自定义指令
const vClickOutside = {
  beforeMount(el: any, binding: any) {
    el.clickOutsideEvent = function(event: Event) {
      if (!(el === event.target || el.contains(event.target))) {
        binding.value(event)
      }
    }
    document.addEventListener('click', el.clickOutsideEvent)
  },
  unmounted(el: any) {
    document.removeEventListener('click', el.clickOutsideEvent)
  }
}

const router = useRouter()
const authStore = useAuthStore()
const userInfo = ref(authStore.userInfo)
const showUserDropdown = ref(false)

// 用户头像计算属性 - 确保中文字符能正确显示
const userAvatar = computed(() => {
  const userName = authStore.userInfo?.name || ''
  if (userName) {
    // 提取姓氏（第一个字符）确保只显示单字
    const familyName = userName.charAt(0)
    // 白底黑字配置：background=ffffff（白色），color=000000（黑色）
    // 添加随机参数避免缓存
    const timestamp = Date.now()
    return `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(familyName)}&background=ffffff&color=000000&_t=${timestamp}`
  }
  const timestamp = Date.now()
  return `https://api.dicebear.com/7.x/initials/svg?seed=User&background=ffffff&color=000000&_t=${timestamp}`
})

// 切换用户下拉菜单
const toggleUserDropdown = () => {
  showUserDropdown.value = !showUserDropdown.value
}

// 关闭用户下拉菜单
const closeUserDropdown = () => {
  showUserDropdown.value = false
}

// 处理退出登录
const handleLogout = () => {
  authStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
  closeUserDropdown()
}

onMounted(() => {
  // 加载本地存储的用户信息
  authStore.loadFromStorage()
})
</script>

<style scoped lang="scss">
#app {
  min-height: 100vh;
  background: #f5f7fa;
  
  .app-header {
    background: #fff;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
    border-bottom: 1px solid #ebeef5;
    height: 60px;
    position: relative;
    
    .header-content {
      display: flex;
      justify-content: space-between;
      align-items: center;
      height: 100%;
      max-width: 1200px;
      margin: 0 auto;
      
      .logo {
        display: flex;
        align-items: center;
        gap: 10px;
        font-size: 20px;
        font-weight: bold;
        color: #409eff;
        
        .el-icon {
          font-size: 24px;
        }
      }
      
      .user-dropdown-container {
        position: relative;
        
        .user-trigger {
          cursor: pointer;
          padding: 8px 12px;
          border-radius: 6px;
          transition: background-color 0.3s;
          display: flex;
          align-items: center;
          gap: 8px;
          
          &:hover {
            background-color: #f5f7fa;
          }
          
          .user-info {
            display: flex;
            align-items: center;
            gap: 8px;
            
            .username {
              font-size: 14px;
              color: #303133;
              font-weight: 500;
            }
            
            .dropdown-icon {
              transition: transform 0.3s;
              color: #909399;
              font-size: 12px;
              
              &.rotate {
                transform: rotate(180deg);
              }
            }
          }
        }
        
        .user-dropdown-menu {
          position: absolute;
          top: 100%;
          right: 0;
          background: #fff;
          border: 1px solid #ebeef5;
          border-radius: 6px;
          box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
          min-width: 200px;
          z-index: 2000;
          margin-top: 4px;
          
          .dropdown-item {
            padding: 12px 16px;
            cursor: pointer;
            transition: background-color 0.3s;
            
            &:hover {
              background-color: #f5f7fa;
            }
            
            &.user-details {
              display: flex;
              align-items: center;
              gap: 12px;
              cursor: default;
              
              .user-details-info {
                .user-name {
                  font-weight: 500;
                  color: #303133;
                  margin-bottom: 2px;
                }
                
                .user-department {
                  font-size: 12px;
                  color: #909399;
                }
              }
            }
            
            &.logout-item {
              display: flex;
              align-items: center;
              gap: 8px;
              color: #f56c6c;
              
              .el-icon {
                font-size: 14px;
              }
              
              span {
                font-size: 14px;
              }
            }
          }
          
          .el-divider {
            margin: 4px 0;
          }
        }
      }
    }
  }
  
  .app-main {
    min-height: calc(100vh - 120px);
    padding: 20px;
    
    @media (max-width: 768px) {
      padding: 10px;
    }
  }
  
  .app-footer {
    background: #fff;
    border-top: 1px solid #ebeef5;
    height: 60px;
    
    .footer-content {
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;
      height: 100%;
      color: #909399;
      font-size: 12px;
      
      p {
        margin: 2px 0;
      }
    }
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>