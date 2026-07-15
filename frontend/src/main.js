import { createApp } from 'vue'
import {
  ElButton,
  ElCheckbox,
  ElDialog,
  ElDropdown,
  ElForm,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElLoading,
  ElSelect,
  ElSwitch
} from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import { Capacitor } from '@capacitor/core'
import { App as CapacitorApp } from '@capacitor/app'

if (import.meta.env.DEV && 'serviceWorker' in navigator) {
  navigator.serviceWorker.getRegistrations().then((registrations) => {
    registrations.forEach((registration) => registration.unregister())
  })
  if ('caches' in window) {
    caches.keys().then((keys) => keys.forEach((key) => caches.delete(key)))
  }
}

const app = createApp(App)
const elementComponents = [
  ElButton,
  ElCheckbox,
  ElDialog,
  ElDropdown,
  ElForm,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElSelect,
  ElSwitch
]

elementComponents.forEach(component => app.use(component))
app.use(ElLoading)
app.use(router)
app.use(createPinia())
app.mount('#app')

if (Capacitor.isNativePlatform()) {
  CapacitorApp.addListener('backButton', () => {
    const path = router.currentRoute.value.path
    if (path !== '/home' && path !== '/login') {
      if (window.history.state?.back) router.back()
      else router.replace('/home')
    } else {
      CapacitorApp.minimizeApp()
    }
  })
}
