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

      <div class="recent-header">
        <h3 class="section-title">📝 最近问答</h3>
      </div>
      <div v-if="conversations.length === 0" class="empty">
        <div class="empty-icon">💬</div>
        <div class="empty-text">暂无记录</div>
      </div>
      <div v-for="conv in conversations" :key="conv.id" class="conv-item" @click="$router.push('/chat/' + conv.id)">
        <span class="conv-icon">{{ conv.sceneIcon || '💬' }}</span>
        <div class="conv-info">
          <div class="conv-title">{{ conv.title }}</div>
          <div class="conv-meta">{{ conv.sceneLabel }} · {{ formatTime(conv.createdAt) }}</div>
        </div>
        <el-icon class="conv-arrow"><ArrowRight /></el-icon>
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
  { key: 'cooking', icon: '🍳', label: '做饭助手', bg: 'linear-gradient(135deg, #fef2f2, #fecaca)' },
  { key: 'shopping', icon: '🛒', label: '买菜指南', bg: 'linear-gradient(135deg, #f0fdf4, #bbf7d0)' },
  { key: 'repair', icon: '🔧', label: '修理指南', bg: 'linear-gradient(135deg, #eff6ff, #bfdbfe)' },
  { key: 'housework', icon: '🏠', label: '家务技巧', bg: 'linear-gradient(135deg, #faf5ff, #e9d5ff)' },
  { key: 'health', icon: '🏥', label: '健康常识', bg: 'linear-gradient(135deg, #fefce8, #fde68a)' },
  { key: 'fashion', icon: '👔', label: '穿搭指南', bg: 'linear-gradient(135deg, #fce7f3, #fbcfe8)' },
  { key: 'etiquette', icon: '🤝', label: '社交礼仪', bg: 'linear-gradient(135deg, #ecfdf5, #a7f3d0)' },
  { key: 'pet', icon: '🐾', label: '宠物照顾', bg: 'linear-gradient(135deg, #fff7ed, #fed7aa)' }
]

onMounted(async () => {
  try { const res = await getConversations(); conversations.value = res.data || [] } catch (e) { console.error(e) }
})

function startChat(scene, label) {
  localStorage.setItem('currentScene', scene); localStorage.setItem('sceneLabel', label); router.push('/chat')
}
function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.header { padding: 20px 20px 8px; }
.header h2 { font-size: 24px; font-weight: 800; color: #111; }
.content { padding: 0 20px 80px; }

.search-box { display: flex; align-items: center; gap: 8px; height: 50px; background: #f0f0f0; border-radius: 25px; padding: 0 20px; margin-bottom: 28px; cursor: pointer; transition: .15s; }
.search-box:active { transform: scale(.97); background: #e8e8e8; }
.placeholder { color: #999; font-size: 14px; font-weight: 400; }

.section-title { font-size: 16px; font-weight: 700; margin-bottom: 14px; color: #222; }

.scene-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-bottom: 32px; }
.scene-card { height: 100px; border-radius: 20px; display: flex; flex-direction: column; align-items: center; justify-content: center; cursor: pointer; transition: .15s; box-shadow: 0 2px 12px rgba(0,0,0,.06); }
.scene-card:active { transform: scale(.95); }
.scene-icon { font-size: 32px; margin-bottom: 6px; }
.scene-label { font-size: 14px; font-weight: 600; color: #222; }

.conv-item { display: flex; align-items: center; padding: 16px 0; border-bottom: 1px solid #eee; cursor: pointer; }
.conv-icon { font-size: 24px; margin-right: 16px; }
.conv-info { flex: 1; }
.conv-title { font-size: 15px; color: #222; font-weight: 500; }
.conv-meta { font-size: 12px; color: #999; margin-top: 4px; }
.conv-arrow { color: #ccc; font-size: 16px; }

.empty { text-align: center; padding: 48px 0; }
.empty-icon { font-size: 40px; margin-bottom: 8px; }
.empty-text { font-size: 14px; color: #999; }

.bottom-tabs { position: fixed; bottom: 0; left: 50%; transform: translateX(-50%); width: 100%; max-width: 480px; height: 60px; background: #fff; border-top: 1px solid #e8e8e8; display: flex; padding-bottom: 4px; }
.tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 11px; color: #999; cursor: pointer; gap: 2px; }
.tab.active { color: #22c55e; }
.tab .el-icon { font-size: 20px; }
</style>
