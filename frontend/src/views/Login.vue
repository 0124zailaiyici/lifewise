<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="bg-circle c1"></div>
      <div class="bg-circle c2"></div>
      <div class="bg-circle c3"></div>
    </div>

    <div class="login-card">
      <div class="logo-area">
        <div class="logo-icon">🌿</div>
        <h1 class="logo-title">LifeWise</h1>
        <p class="logo-sub">你的 AI 生活助手</p>
      </div>

      <template v-if="!isRegister">
        <el-form :model="form" class="form" @keyup.enter="handleLogin">
          <el-input v-model="form.email" placeholder="邮箱" size="large" class="input-field" />
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" class="input-field" show-password />
          <el-button type="success" size="large" class="login-btn" @click="handleLogin" :loading="loading">
            登 录
          </el-button>
        </el-form>
        <div class="switch-text">
          还没有账号？
          <el-button text type="primary" @click="isRegister = true">立即注册</el-button>
        </div>
      </template>

      <template v-else>
        <el-form @keyup.enter="handleRegister" class="form">
          <el-input v-model="registerForm.username" placeholder="用户名" size="large" class="input-field" />
          <el-input v-model="registerForm.email" placeholder="邮箱" size="large" class="input-field" />
          <el-input v-model="registerForm.password" type="password" placeholder="密码（至少6位）" size="large" class="input-field" show-password />
          <el-button type="success" size="large" class="login-btn" @click="handleRegister" :loading="loading">注 册</el-button>
        </el-form>
        <div class="switch-text">
          已有账号？
          <el-button text type="primary" @click="isRegister = false">去登录</el-button>
        </div>
      </template>
    </div>
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
const form = reactive({ email: 'demo@test.com', password: '123456' })
const registerForm = reactive({ username: '', email: '', password: '' })

async function handleLogin() {
  if (!form.email || !form.password) { ElMessage.warning('请填写邮箱和密码'); return }
  loading.value = true
  try {
    const res = await login(form.email, form.password)
    userStore.setUser(res.data)
    localStorage.setItem('token', res.data.token)
    ElMessage.success('登录成功')
    router.push('/home')
  } catch (e) { ElMessage.error(e.response?.data?.message || '登录失败') }
  finally { loading.value = false }
}

async function handleRegister() {
  if (!registerForm.username || !registerForm.email || !registerForm.password) { ElMessage.warning('请填写所有字段'); return }
  loading.value = true
  try {
    await register(registerForm.username, registerForm.email, registerForm.password)
    ElMessage.success('注册成功，请登录')
    isRegister.value = false
  } catch (e) { ElMessage.error(e.response?.data?.message || '注册失败') }
  finally { loading.value = false }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e8f5e9, #c8e6c9, #a5d6a7);
  position: relative;
  overflow: hidden;
}
.login-bg { position: absolute; width: 100%; height: 100%; top: 0; left: 0; pointer-events: none; }
.bg-circle { position: absolute; border-radius: 50%; }
.c1 { width: 500px; height: 500px; background: #4caf50; opacity: 0.1; top: -150px; right: -150px; }
.c2 { width: 350px; height: 350px; background: #2e7d32; opacity: 0.08; bottom: -100px; left: -100px; }
.c3 { width: 200px; height: 200px; background: #66bb6a; opacity: 0.12; top: 50%; left: -80px; }

.login-card {
  width: 380px;
  background: #fff;
  border-radius: 24px;
  padding: 44px 36px;
  box-shadow: 0 8px 40px rgba(0,0,0,.12);
  position: relative;
  z-index: 1;
}
.logo-area { text-align: center; margin-bottom: 36px; }
.logo-icon { font-size: 64px; margin-bottom: 10px; display: block; }
.logo-title { font-size: 28px; font-weight: 800; color: #1a1a1a; margin: 0; letter-spacing: -.5px; }
.logo-sub { font-size: 14px; color: #888; margin: 6px 0 0; font-weight: 400; }
.form { width: 100%; }
.input-field { margin-bottom: 18px; }
.login-btn { width: 100%; height: 48px; font-size: 16px; font-weight: 600; border-radius: 12px; margin-top: 6px; }
.switch-text { text-align: center; margin-top: 22px; font-size: 13px; color: #888; }
</style>
