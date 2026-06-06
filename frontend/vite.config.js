import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: true,
    proxy: {
      // 代理 /api 请求到后端 :8080
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 代理 /uploads 图片请求
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 代理食物图片
      '/food-image': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
