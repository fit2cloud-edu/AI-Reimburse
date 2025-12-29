<template>
  <div id="app">
    <el-container>
      <el-header class="app-header">
        <div class="header-content">
          <div class="logo">
            <el-icon><Money /></el-icon>
            <span>AI发票报销</span>
          </div>
          <div class="user-info" v-if="authStore.isLoggedIn">
            <el-avatar :size="32" :src="userAvatar">
              {{ authStore.userInfo?.name?.charAt(0) }}
            </el-avatar>
            <span class="username">{{ authStore.userInfo?.name }}</span>
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
import { Money } from '@element-plus/icons-vue'
import { companyConfig } from './config/company'

const router = useRouter()
const authStore = useAuthStore()
const userInfo = ref(authStore.userInfo)

// 用户头像计算属性 - 确保中文字符能正确显示
const userAvatar = computed(() => {
  const userName = authStore.userInfo?.name || ''
  if (userName) {
    // 如果用户名包含中文字符，确保能正确显示
    return `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(userName)}`
  }
  return 'https://api.dicebear.com/7.x/initials/svg?seed=User'
})

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
      
      .user-info {
        display: flex;
        align-items: center;
        gap: 10px;
        
        .username {
          font-size: 14px;
          color: #303133;
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