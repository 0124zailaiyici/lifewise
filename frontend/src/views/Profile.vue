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
          <div class="phone">{{ user?.phone || '' }}</div>
        </div>
      </div>

      <div class="menu-section">
        <div class="menu-label">功能</div>
        <div class="menu-list">
          <div class="menu-item" @click="$router.push('/history')">
            <el-icon><Timer /></el-icon>
            <span>历史记录</span>
            <el-icon class="menu-arrow"><ArrowRight /></el-icon>
          </div>
          <div class="menu-item" @click="$router.push('/favorites')">
            <el-icon><Star /></el-icon>
            <span>我的收藏</span>
            <el-icon class="menu-arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>

      <div class="menu-section">
        <div class="menu-label">账号</div>
        <div class="menu-list">
          <div class="menu-item logout" @click="handleLogout">
            <el-icon><SwitchButton /></el-icon>
            <span>退出登录</span>
          </div>
        </div>
      </div>

      <div class="footer-info">
        <div class="app-name">LifeWise v1.0</div>
        <div class="app-desc">AI 生活常识助手</div>
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
import { HomeFilled, Timer, Star, User, ArrowRight, SwitchButton } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const user = computed(() => userStore.user)

function handleLogout() {
  ElMessageBox.confirm('确定退出登录吗？', '提示', {
    confirmButtonText: '退出', cancelButtonText: '取消', type: 'warning'
  }).then(() => {
    userStore.logout()
    router.push('/login')
  }).catch(() => {})
}
</script>

<style scoped>
.page-header { text-align: center; padding: 18px 20px; border-bottom: 1px solid #e0e0e0; }
.page-header h3 { font-size: 17px; font-weight: 600; color: #111; }
.content { padding: 20px 20px 80px; }

.user-card { display: flex; align-items: center; gap: 16px; padding: 20px; background: linear-gradient(135deg, #f0fdf4, #dcfce7); border-radius: 18px; margin-bottom: 28px; }
.avatar { width: 54px; height: 54px; border-radius: 50%; background: linear-gradient(135deg, #22c55e, #16a34a); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 24px; font-weight: 700; box-shadow: 0 3px 12px rgba(34,197,94,.25); }
.username { font-size: 18px; font-weight: 600; color: #111; }
.phone { font-size: 13px; color: #666; margin-top: 4px; }

.menu-section { margin-bottom: 24px; }
.menu-label { font-size: 12px; color: #999; margin-bottom: 8px; padding-left: 4px; font-weight: 500; }
.menu-list { background: #fff; border-radius: 14px; border: 1px solid #f0f0f0; overflow: hidden; }
.menu-item { display: flex; align-items: center; padding: 16px; border-bottom: 1px solid #f5f5f5; cursor: pointer; gap: 12px; color: #333; font-size: 14px; transition: .1s; }
.menu-item:last-child { border: none; }
.menu-item:active { background: #fafafa; }
.menu-item .el-icon:first-child { font-size: 18px; color: #666; }
.menu-arrow { margin-left: auto; color: #ccc; font-size: 14px; }
.menu-item.logout { color: #ef4444; }
.menu-item.logout .el-icon:first-child { color: #ef4444; }

.footer-info { text-align: center; margin-top: 48px; }
.app-name { font-size: 13px; color: #ccc; font-weight: 500; }
.app-desc { font-size: 11px; color: #bbb; margin-top: 4px; }

.bottom-tabs { position: fixed; bottom: 0; left: 50%; transform: translateX(-50%); width: 100%; max-width: 480px; height: 60px; background: #fff; border-top: 1px solid #f0f0f0; display: flex; padding-bottom: 4px; }
.tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 11px; color: #999; cursor: pointer; gap: 2px; }
.tab.active { color: #22c55e; }
.tab .el-icon { font-size: 20px; }
</style>