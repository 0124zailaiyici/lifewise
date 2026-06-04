<template>
  <div class="page-container login-page">
    <div class="login-wrap">
      <div class="logo">🌿</div>
      <h1 class="title">LifeWise</h1>
      <p class="subtitle">你的 AI 生活助手</p>

      <el-form :model="form" class="form">
        <el-input v-model="form.email" placeholder="邮箱" size="large" class="input" />
        <el-input v-model="form.password" type="password" placeholder="密码" size="large" class="input" show-password />
        <el-button type="success" size="large" class="btn" @click="handleLogin" :loading="loading">
          登 录
        </el-button>
      </el-form>

      <el-button text type="primary" @click="isRegister = !isRegister" class="switch-btn">
        {{ isRegister ? '已有账号？去登录' : '没有账号？去注册' }}
      </el-button>
    </div>

    <!-- 注册弹窗 -->
    <el-dialog v-model="isRegister" title="注册" width="85%">
      <el-form :model="registerForm">
        <el-input v-model="registerForm.username" placeholder="用户名" class="input" />
        <el-input v-model="registerForm.email" placeholder="邮箱" class="input" />
        <el-input v-model="registerForm.password" type="password" placeholder="密码" class="input" show-password />
      </el-form>
      <template #footer>
        <el-button type="success" @click="handleRegister" :loading="loading">注册</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { login, register } from '../api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const isRegister = ref(false)

const form = reactive({ email: 'test@test.com', password: '123456' })
const registerForm = reactive({ username: '', email: '', password: '' })

async function handleLogin() {
  loading.value = true
  try {
    const res = await login(form.email, form.password)
    userStore.setUser(res.data)
    ElMessage.success('登录成功')
    router.push('/home')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '登录失败')
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  loading.value = true
  try {
    await register(registerForm.username, registerForm.email, registerForm.password)
    ElMessage.success('注册成功，请登录')
    isRegister.value = false
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '注册失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page { display: flex; align-items: center; justify-content: center; }
.login-wrap { text-align: center; padding: 40px 32px; width: 100%; }
.logo { font-size: 60px; margin-bottom: 8px; }
.title { font-size: 28px; font-weight: bold; color: #333; }
.subtitle { font-size: 14px; color: #999; margin-bottom: 40px; }
.form { width: 100%; }
.input { margin-bottom: 16px; }
.btn { width: 100%; margin-top: 8px; }
.switch-btn { margin-top: 16px; }
</style>
