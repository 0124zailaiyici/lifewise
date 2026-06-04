<template>
  <div class="page-container">
    <div class="header">
      <h2>LifeWise</h2>
    </div>

    <div class="content">
      <div class="search-box" @click="$router.push('/chat')">
        <el-icon><Search /></el-icon>
        <span class="placeholder">今天想解决什么问题？</span>
      </div>

      <h3 class="section-title">✨ 选个场景开始</h3>
      <div class="scene-grid">
        <div v-for="s in scenes" :key="s.key" class="scene-card" :style="{ background: s.bg }"
             @click="startChat(s.key, s.label)">
          <span class="scene-icon">{{ s.icon }}</span>
          <span class="scene-label">{{ s.label }}</span>
        </div>
      </div>

      <h3 class="section-title">📝 最近问答</h3>
      <div v-if="conversations.length === 0" class="empty">暂无记录，开始你的第一个问题吧</div>
      <div v-for="conv in conversations" :key="conv.id" class="conv-item" @click="$router.push('/chat/' + conv.id)">
        <span class="conv-icon">{{ conv.sceneIcon || '💬' }}</span>
        <div class="conv-info">
          <div class="conv-title">{{ conv.title }}</div>
          <div class="conv-meta">{{ conv.sceneLabel }} · {{ formatTime(conv.createdAt) }}</div>
        </div>
        <el-icon><ArrowRight /></el-icon>
      </div>
    </div>

    <div class="bottom-tabs">
      <div class="tab active"><el-icon><HomeFilled /></el-icon><span>首页</span></div>
      <div class="tab" @click="$router.push('/history')"><el-icon><Timer /></el-icon><span>历史</span></div>
      <div class="tab" @click="$router.push('/favorites')"><el-icon><Star /></el-icon><span>收藏</span></div>
      <div class="tab" @click="$router.push('/profile')"><el-icon><User /></el-icon><span>我的</span></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getConversations } from '../api'
import { Search, ArrowRight, HomeFilled, Timer, Star, User } from '@element-plus/icons-vue'

const router = useRouter()
const conversations = ref([])

const scenes = [
  { key: 'cooking', icon: '🍳', label: '做饭助手', bg: '#fef2f2' },
  { key: 'shopping', icon: '🛒', label: '买菜指南', bg: '#f0fdf4' },
  { key: 'repair', icon: '🔧', label: '修理指南', bg: '#eff6ff' },
  { key: 'housework', icon: '🏠', label: '家务技巧', bg: '#faf5ff' }
]

onMounted(async () => {
  try {
    const res = await getConversations()
    conversations.value = res.data || []
  } catch (e) {
    console.error(e)
  }
})

function startChat(scene, label) {
  localStorage.setItem('currentScene', scene)
  localStorage.setItem('sceneLabel', label)
  router.push('/chat')
}

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.header { padding: 16px 20px; }
.header h2 { font-size: 20px; font-weight: 700; color: #333; }
.content { padding: 0 20px 80px; }
.search-box { display: flex; align-items: center; gap: 8px; height: 44px; background: #f5f5f5; border-radius: 22px; padding: 0 16px; margin-bottom: 24px; cursor: pointer; }
.placeholder { color: #999; font-size: 14px; }
.section-title { font-size: 15px; font-weight: 600; margin-bottom: 12px; color: #333; }
.scene-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 24px; }
.scene-card { height: 90px; border-radius: 16px; display: flex; flex-direction: column; align-items: center; justify-content: center; cursor: pointer; transition: .2s; }
.scene-card:active { transform: scale(.95); }
.scene-icon { font-size: 28px; margin-bottom: 4px; }
.scene-label { font-size: 13px; font-weight: 500; color: #333; }
.conv-item { display: flex; align-items: center; padding: 12px 0; border-bottom: 1px solid #f5f5f5; cursor: pointer; }
.conv-icon { font-size: 20px; margin-right: 12px; }
.conv-info { flex: 1; }
.conv-title { font-size: 14px; color: #333; }
.conv-meta { font-size: 12px; color: #999; margin-top: 2px; }
.empty { color: #ccc; text-align: center; padding: 40px 0; font-size: 14px; }
.bottom-tabs { position: fixed; bottom: 0; left: 50%; transform: translateX(-50%); width: 100%; max-width: 480px; height: 56px; background: #fff; border-top: 1px solid #eee; display: flex; }
.tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 11px; color: #999; cursor: pointer; gap: 2px; }
.tab.active { color: #22c55e; }
.tab .el-icon { font-size: 20px; }
</style>
