import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css';
import zhCn from 'element-plus/es/locale/lang/zh-cn' // 修改为正确的导入路径
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './assets/main.css'
import axios from 'axios'

// 添加请求拦截器监控
axios.interceptors.request.use(
  (config) => {
    console.log('请求发送:', config.method?.toUpperCase(), config.url)
    return config
  },
  (error) => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 添加响应拦截器监控
axios.interceptors.response.use(
  (response) => {
    console.log('请求成功:', response.status, response.config.url)
    return response
  },
  (error) => {
    console.error('响应错误:', error.response?.status, error.response?.config?.url)
    return Promise.reject(error)
  }
)


const app = createApp(App)

// 注册所有Element Plus图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, {
  locale: zhCn,
})

app.mount('#app')