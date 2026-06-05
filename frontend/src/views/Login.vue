<template>
  <div class="login-page">
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
          <el-button type="success" size="large" class="login-btn" @click="handleLogin" :loading="loading">登 录</el-button>
        </el-form>
        <div class="switch-text">还没有账号？<el-button text type="primary" @click="mode = 'register'">立即注册</el-button></div>
      </template>

      <!-- 注册 -->
      <template v-if="mode === 'register'">
        <el-form @keyup.enter="handleRegister" class="form">
          <el-input v-model="registerForm.username" placeholder="用户名" size="large" class="input-field" />
          <el-input v-model="registerForm.phone" placeholder="手机号" size="large" class="input-field" maxlength="11" />
          <el-input v-model="registerForm.password" type="password" placeholder="密码（至少6位）" size="large" class="input-field" show-password />
          <el-button type="success" size="large" class="login-btn" @click="handleRegister" :loading="loading">注 册</el-button>
        </el-form>
        <div class="switch-text">已有账号？<el-button text type="primary" @click="mode = 'login'">去登录</el-button></div>
      </template>

      <!-- 忘记密码 -->
      <template v-if="mode === 'forgot'">
        <template v-if="forgotStep === 1">
          <p class="forgot-desc">请输入注册时使用的手机号，我们将发送验证码</p>
          <el-form @keyup.enter="handleSendCode" class="form">
            <el-input v-model="forgotForm.phone" placeholder="手机号" size="large" class="input-field" maxlength="11" />
            <el-button type="success" size="large" class="login-btn" @click="handleSendCode" :loading="codeLoading">发送验证码</el-button>
          </el-form>
        </template>
        <template v-if="forgotStep === 2">
          <p class="forgot-desc">请输入验证码和新密码</p>
          <el-form @keyup.enter="handleResetPassword" class="form">
            <el-input v-model="forgotForm.code" placeholder="验证码" size="large" class="input-field" maxlength="6" />
            <el-input v-model="forgotForm.newPassword" type="password" placeholder="新密码（至少6位）" size="large" class="input-field" show-password />
            <el-button type="success" size="large" class="login-btn" @click="handleResetPassword" :loading="loading">重置密码</el-button>
          </el-form>
        </template>
        <div class="switch-text"><el-button text type="primary" @click="mode = 'login'">返回登录</el-button></div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { login as loginApi, register as registerApi, forgotPassword, resetPassword } from '../api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const mode = ref('login')
const loading = ref(false)
const codeLoading = ref(false)
const forgotStep = ref(1)
const form = reactive({ phone: '13800138000', password: 'test123' })
const registerForm = reactive({ username: '', phone: '', password: '' })
const forgotForm = reactive({ phone: '', code: '', newPassword: '' })

async function handleLogin() {
  if (!form.phone || !form.password) { ElMessage.warning('请填写手机号和密码'); return }
  loading.value = true
  try {
    const res = await loginApi(form.phone, form.password)
    userStore.setUser(res.data)
    localStorage.setItem('token', res.data.token)
    localStorage.setItem('user', JSON.stringify(res.data))
    ElMessage.success('登录成功')
    router.push('/home')
  } catch (e) { ElMessage.error(e.response?.data?.message || '登录失败') }
  finally { loading.value = false }
}
async function handleRegister() {
  if (!registerForm.username || !registerForm.phone || !registerForm.password) { ElMessage.warning('请填写完整信息'); return }
  if (registerForm.password.length < 6) { ElMessage.warning('密码至少6位'); return }
  loading.value = true
  try {
    await registerApi(registerForm.username, registerForm.phone, registerForm.password)
    ElMessage.success('注册成功，请登录'); mode.value = 'login'; form.phone = registerForm.phone
  } catch (e) { ElMessage.error(e.response?.data?.message || '注册失败') }
  finally { loading.value = false }
}
async function handleSendCode() {
  if (!forgotForm.phone) { ElMessage.warning('请输入手机号'); return }
  codeLoading.value = true
  try {
    await forgotPassword(forgotForm.phone)
    ElMessage.success('验证码已发送（开发模式：123456）'); forgotStep.value = 2
  } catch (e) { ElMessage.error(e.response?.data?.message || '发送失败') }
  finally { codeLoading.value = false }
}
async function handleResetPassword() {
  if (!forgotForm.code || !forgotForm.newPassword) { ElMessage.warning('请填写完整信息'); return }
  if (forgotForm.newPassword.length < 6) { ElMessage.warning('密码至少6位'); return }
  loading.value = true
  try {
    await resetPassword(forgotForm.phone, forgotForm.code, forgotForm.newPassword)
    ElMessage.success('密码重置成功，请登录'); mode.value = 'login'
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
  background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 50%, #bbf7d0 100%);
  padding: 20px;
}
.login-card {
  width: 100%;
  max-width: 380px;
  background: #fff;
  border-radius: 20px;
  padding: 36px 28px 28px;
  box-shadow: 0 8px 30px rgba(0,0,0,.08);
}
.logo-area { text-align: center; margin-bottom: 28px; }
.logo-icon { font-size: 48px; margin-bottom: 8px; }
.logo-title {
  font-size: 28px;
  font-weight: 800;
  background: linear-gradient(135deg, #22c55e, #059669);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin: 0;
}
.logo-sub { font-size: 14px; color: #999; margin-top: 6px; }
.form { margin-bottom: 8px; }
.input-field { margin-bottom: 16px; }
.forgot-row { text-align: right; margin-bottom: 8px; }
.login-btn { width: 100%; height: 46px; font-size: 16px; margin-top: 4px; }
.switch-text { text-align: center; font-size: 14px; color: #999; margin-top: 20px; }
.forgot-desc { font-size: 14px; color: #666; margin-bottom: 20px; text-align: center; line-height: 1.6; }
</style>
