import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const aiDevProxyTarget = String(env.VITE_AI_DEV_PROXY_TARGET || '').trim().replace(/\/+$/, '')

   return {
   plugins: [vue()],
   test: {
     environment: 'jsdom',
     include: ['tests/**/*.component.spec.js']
   },
  // Capacitor 本地资源加载需要相对路径
  base: './',
  build: {
    rolldownOptions: {
      onLog(level, log, handler) {
        // Element Plus 依赖中的 PURE 注释位置会被 Rolldown 误报，不影响代码执行或摇树优化结果。
        if (log.code === 'INVALID_ANNOTATION' && log.id?.includes('/node_modules/@vueuse/core/')) return
        handler(level, log)
      },
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('jspdf')) return 'vendor-pdf'
          if (id.includes('html2canvas')) return 'vendor-canvas'
          if (id.includes('element-plus') || id.includes('@element-plus')) return 'vendor-element'
          if (id.includes('@vueuse')) return 'vendor-vueuse'
          if (id.includes('@floating-ui') || id.includes('@popperjs')) return 'vendor-floating'
          if (id.includes('lodash') || id.includes('dayjs') || id.includes('async-validator')) return 'vendor-element-utils'
          if (id.includes('/vue/') || id.includes('vue-router') || id.includes('pinia')) return 'vendor-vue'
          if (id.includes('axios')) return 'vendor-api'
          return 'vendor'
        }
      }
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/food-image': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      ...(aiDevProxyTarget ? {
        '/__lifewise_ai': {
          target: aiDevProxyTarget,
          changeOrigin: true,
          rewrite: path => path.replace(/^\/__lifewise_ai/, '')
        }
      } : {})
    }
  }
  }
})
