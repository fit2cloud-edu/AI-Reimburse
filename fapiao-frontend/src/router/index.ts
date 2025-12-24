import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/store/auth'  // 确认导出

const routes: Array<RouteRecordRaw> = [
  {
    path: '/',
    name: 'Reimbursement',
    component: () => import('@/views/Reimbursement.vue'),
    meta: {
      requiresAuth: true
    }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { 
      requiresAuth: false,
      keepAlive: false // 确保登录页面不缓存
  }
  },
  // 添加404页面处理
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()

    // 调试日志
  console.log('路由守卫触发:', {
    to: to.path,
    requiresAuth: to.meta.requiresAuth,
    isLoggedIn: authStore.isLoggedIn,
    userInfo: authStore.userInfo
  })
  
  // 如果路由需要认证但用户未登录
  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    console.log('需要认证，重定向到登录页')
    next('/login')
  } 
  // 如果用户已登录但访问登录页，重定向到首页
  else if (to.path === '/login' && authStore.isLoggedIn) {
    console.log('已登录用户访问登录页，重定向到首页')
    next('/')
  }
  else {
    next()
  }
})

export default router