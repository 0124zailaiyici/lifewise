<template>
  <div class="page-container">
    <div class="page-header">
      <h3>👤 我的</h3>
    </div>

    <div class="content">
      <div class="user-card">
        <div class="avatar">{{ (user?.username || 'U')[0].toUpperCase() }}</div>
        <div class="user-info">
          <div class="username">{{ user?.username || '未登录' }}</div>
          <div class="email">{{ user?.email || '' }}</div>
        </div>
      </div>

      <div class="menu-list">
        <div class="menu-item" @click="$router.push('/history')">
          <el-icon><Timer /></el-icon>
          <span>历史记录</span>
          <el-icon><ArrowRight /></el-icon>
        </div>
        <div class="menu-item" @click="$router.push('/favorites')">
          <el-icon><Star /></el-icon>
          <span>我的收藏</span>
          <el-icon><ArrowRight /></el-icon>
        </div>
      </div>

      <div class="logout-section">
        <el-button type="danger" plain size="large" class="logout-btn" @click="handleLogout">
          退出登录
        </el-button>
      </div>
    </div>

    <div class="bottom-tabs">
      <div class="tab" @click="$router.push('/home')"><el-icon><HomeFilled /></el-icon><span>首页</span></div>
      <div class="tab" @click="$router.push('/history')"><el-icon><Timer /></el-icon><span>历史</span></div>
      <div class="tab" @click="$router.push('/favorites')"><el-icon><Star /></el-icon><span>收藏</span></div>
      <div class="tab active"><el-icon><User /></el-icon><span>我的</span></div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { HomeFilled, Timer, Star, User, ArrowRight } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const user = computed(() => userStore.user)

function handleLogout() {
  ElMessageBox.confirm('确定退出登录吗？', '提示', {
    confirmButtonText: '退出',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    userStore.logout()
    router.push('/login')
  }).catch(() => {})
}
</script>

<style scoped>
.page-header { text-align: center; padding: 16px 20px; border-bottom: 1px solid #eee; }
.page-header h3 { font-size: 17px; font-weight: 600; color: #333; }
.content { padding: 16px 20px 80px; }
.user-card { display: flex; align-items: center; gap: 16px; padding: 20px; background: #fafafa; border-radius: 16px; margin-bottom: 20px; }
.avatar { width: 52px; height: 52px; border-radius: 50%; background: #22c55e; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 22px; font-weight: 600; }
.username { font-size: 17px; font-weight: 600; color: #333; }
.email { font-size: 13px; color: #999; margin-top: 4px; }
.menu-list { background: #fff; border-radius: 12px; border: 1px solid #f0f0f0; }
.menu-item { display: flex; align-items: center; padding: 16px; border-bottom: 1px solid #f5f5f5; cursor: pointer; gap: 12px; color: #333; font-size: 14px; }
.menu-item:last-child { border: none; }
.menu-item .el-icon:first-child { font-size: 18px; color: #666; }
.menu-item .el-icon:last-child { margin-left: auto; color: #ccc; font-size: 14px; }
.logout-section { margin-top: 40px; }
.logout-btn { width: 100%; }
.bottom-tabs { position: fixed; bottom: 0; left: 50%; transform: translateX(-50%); width: 100%; max-width: 480px; height: 56px; background: #fff; border-top: 1px solid #eee; display: flex; }
.tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 11px; color: #999; cursor: pointer; gap: 2px; }
.tab.active { color: #22c55e; }
.tab .el-icon { font-size: 20px; }
</style>
