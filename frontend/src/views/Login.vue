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

      <!-- 登录 -->
      <template v-if="mode === 'login'">
        <el-form :model="form" class="form" @keyup.enter="handleLogin">
          <el-input v-model="form.phone" placeholder="手机号" size="large" class="input-field" maxlength="11" />
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" class="input-field" show-password />
          <div class="forgot-row">
            <el-button text type="primary" size="small" @click="mode = 'forgot'">忘记密码？</el-button>
          </div>
          <el-button type="success" size="large" class="login-btn" @click="handleLogin" :loading="loading">
            登 录
          </el-button>
        </el-form>
        <div class="switch-text">
          还没有账号？
          <el-button text type="primary" @click="mode = 'register'">立即注册</el-button>
        </div>
      </template>

      <!-- 注册 -->
      <template v-if="mode === 'register'">
        <el-form @keyup.enter="handleRegister" class="form">
          <el-input v-model="registerForm.username" placeholder="用户名" size="large" class="input-field" />
          <el-input v-model="registerForm.phone" placeholder="手机号" size="large" class="input-field" maxlength="11" />
          <el-input v-model="registerForm.password" type="password" placeholder="密码（至少6位）" size="large" class="input-field" show-password />
          <el-button type="success" size="large" class="login-btn" @click="handleRegister" :loading="loading">注 册</el-button>
        </el-form>
        <div class="switch-text">
          已有账号？
          <el-button text type="primary" @click="mode = 'login'">去登录</el-button>
        </div>
      </template>

      <!-- 忘记密码 -->
      <template v-if="mode === 'forgot'">
        <template v-if="forgotStep === 1">
          <p class="forgot-desc">请输入注册时使用的手机号，我们将发送验证码</p>
          <el-form @keyup.enter="handleSendCode" class="form">
            <el-input v-model="forgotForm.phone" placeholder="手机号" size="large" class="input-field" maxlength="11" />
            <el-button type="success" size="large" class="login-btn" @click="handleSendCode" :loading="codeLoading">
              发送验证码
            </el-button>
          </el-form>
        </template>
        <template v-if="forgotStep === 2">
          <p class="forgot-desc">请输入验证码和新密码</p>
          <el-form @keyup.enter="handleResetPassword" class="form">
            <el-input v-model="forgotForm.code" placeholder="验证码" size="large" class="input-field" maxlength="6" />
            <el-input v-model="forgotForm.newPassword" type="password" placeholder="新密码（至少6位）" size="large" class="input-field" show-password />
            <el-button type="success" size="large" class="login-btn" @click="handleResetPassword" :loading="loading">
              重置密码
            </el-button>
          </el-form>
        </template>
        <div class="switch-text">
          <el-button text type="primary" @click="mode = 'login'">返回登录</el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { login, register, forgotPassword, resetPassword } from '../api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const codeLoading = ref(false)
const mode = ref('login')
const forgotStep = ref(1)

const form = reactive({ phone: '13800138000', password: '123456' })
const registerForm = reactive({ username: '', phone: '', password: '' })
const forgotForm = reactive({ phone: '', code: '', newPassword: '' })

async function handleLogin() {
  if (!form.phone || !form.password) { ElMessage.warning('请填写手机号和密码'); return }
  loading.value = true
  try {
    const res = await login(form.phone, form.password)
    userStore.setUser(res.data)
    localStorage.setItem('token', res.data.token)
    ElMessage.success('登录成功')
    router.push('/home')
  } catch (e) { ElMessage.error(e.response?.data?.message || '登录失败') }
  finally { loading.value = false }
}

async function handleRegister() {
  if (!registerForm.username || !registerForm.phone || !registerForm.password) { ElMessage.warning('请填写所有字段'); return }
  if (registerForm.phone.length !== 11) { ElMessage.warning('请输入正确的手机号'); return }
  loading.value = true
  try {
    await register(registerForm.username, registerForm.phone, registerForm.password)
    ElMessage.success('注册成功，请登录')
    mode.value = 'login'
  } catch (e) { ElMessage.error(e.response?.data?.message || '注册失败') }
  finally { loading.value = false }
}

async function handleSendCode() {
  if (!forgotForm.phone || forgotForm.phone.length !== 11) { ElMessage.warning('请输入正确的手机号'); return }
  codeLoading.value = true
  try {
    await forgotPassword(forgotForm.phone)
    ElMessage.success('验证码已发送（控制台查看）')
    forgotStep.value = 2
  } catch (e) { ElMessage.error(e.response?.data?.message || '发送失败') }
  finally { codeLoading.value = false }
}

async function handleResetPassword() {
  if (!forgotForm.code || !forgotForm.newPassword) { ElMessage.warning('请填写验证码和新密码'); return }
  if (forgotForm.newPassword.length < 6) { ElMessage.warning('密码至少6位'); return }
  loading.value = true
  try {
    await resetPassword(forgotForm.phone, forgotForm.code, forgotForm.newPassword)
    ElMessage.success('密码重置成功，请登录')
    mode.value = 'login'
    forgotStep.value = 1
    forgotForm.code = ''
    forgotForm.newPassword = ''
  } catch (e) { ElMessage.error(e.response?.data?.message || '重置失败') }
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
.forgot-row { text-align: right; margin-top: -10px; margin-bottom: 8px; }
.forgot-desc { font-size: 13px; color: #888; text-align: center; margin-bottom: 20px; line-height: 1.6; }
.login-btn { width: 100%; height: 48px; font-size: 16px; font-weight: 600; border-radius: 12px; margin-top: 6px; }
.switch-text { text-align: center; margin-top: 22px; font-size: 13px; color: #888; }
</style>
