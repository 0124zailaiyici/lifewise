<template>
  <div class="page-container">
    <div class="header">
      <div class="header-left">
        <h2 class="logo">LifeWise</h2>
        <p class="greeting">{{ greeting }}</p>
      </div>
      <div class="header-right">
        <el-button text class="header-icon-btn" @click="$router.push('/search')">
          <el-icon :size="22"><Search /></el-icon>
        </el-button>
        <el-button text class="header-icon-btn" @click="$router.push('/profile')">
          <el-icon :size="22"><User /></el-icon>
        </el-button>
      </div>
    </div>

    <div class="content">
      <!-- AI 快捷入口 -->
      <div class="quick-chat" @click="$router.push('/chat')">
        <div class="quick-icon">
          <el-icon :size="28"><ChatDotSquare /></el-icon>
        </div>
        <div class="quick-text">
          <div class="quick-title">今天想解决什么问题？</div>
          <div class="quick-sub">问问 AI 生活助手，从做饭到修理都能帮</div>
        </div>
        <el-icon class="quick-arrow"><ArrowRight /></el-icon>
      </div>

      <!-- 场景 -->
      <h3 class="section-title">✨ 场景</h3>
      <div class="scene-grid">
        <div v-for="s in scenes" :key="s.key" class="scene-card" :style="{ backgroundImage: s.bg }"
             @click="startChat(s.key, s.label)">
          <span class="scene-icon" v-html="svgIcon(s.icon)"></span>
          <span class="scene-label">{{ s.label }}</span>
        </div>
      </div>

      <!-- 最近问答 -->
      <div v-if="conversations.length > 0">
        <div class="recent-header">
          <h3 class="section-title">💬 最近问答</h3>
          <el-button text type="primary" size="small" @click="$router.push('/history')">查看全部 →</el-button>
        </div>
        <div v-for="conv in conversations" :key="conv.id" class="conv-item" @click="$router.push('/chat/' + conv.id)">
          <span class="conv-icon">{{ conv.sceneIcon || '💬' }}</span>
          <div class="conv-info">
            <div class="conv-title">{{ displayConversationTitle(conv) }}</div>
            <div class="conv-meta">{{ conv.sceneLabel }} · {{ formatTime(conv.createdAt) }}</div>
          </div>
          <el-icon class="conv-arrow"><ArrowRight /></el-icon>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-else class="empty-state">
        <div class="empty-icon">💡</div>
        <div class="empty-text">点击上方开始对话</div>
        <div class="empty-hint">选择场景或直接提问，AI 会帮你解答生活问题</div>
      </div>
    </div>

    <!-- 底部导航 -->
    <div class="bottom-tabs">
      <div class="tab active"><el-icon><HomeFilled /></el-icon><span>首页</span></div>
      <div class="tab" @click="$router.push('/history')"><el-icon><Timer /></el-icon><span>历史</span></div>
      <div class="tab" @click="$router.push('/favorites')"><el-icon><Star /></el-icon><span>收藏</span></div>
      <div class="tab" @click="$router.push('/profile')"><el-icon><User /></el-icon><span>我的</span></div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getConversations } from '../api'
import { Search, User, ArrowRight, ChatDotSquare, HomeFilled, Timer, Star } from '@element-plus/icons-vue'

const router = useRouter()
const conversations = ref([])

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了，注意休息 🌙'
  if (h < 9) return '早上好！☀️'
  if (h < 12) return '上午好 🌤️'
  if (h < 14) return '中午好 🌞'
  if (h < 18) return '下午好 🌅'
  return '晚上好 🌆'
})

const scenes = [
  { key: 'cooking', icon: 'cooking', label: '做饭助手', bg: 'url(https://images.unsplash.com/photo-1556909114-f6e7ad7d3136?w=400&h=300&fit=crop&auto=format)' },
  { key: 'shopping', icon: 'shopping', label: '买菜指南', bg: 'url(https://images.unsplash.com/photo-1610832958506-aa56368176cf?w=400&h=300&fit=crop&auto=format)' },
  { key: 'repair', icon: 'repair', label: '修理指南', bg: 'url(https://images.unsplash.com/photo-1581783898377-1c85bf937427?w=400&h=300&fit=crop&auto=format)' },
  { key: 'housework', icon: 'housework', label: '家务技巧', bg: 'url(https://images.unsplash.com/photo-1584622650111-993a426fbf0a?w=400&h=300&fit=crop&auto=format)' },
  { key: 'health', icon: 'health', label: '健康常识', bg: 'url(https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?w=400&h=300&fit=crop&auto=format)' },
  { key: 'fashion', icon: 'fashion', label: '穿搭指南', bg: 'url(https://images.unsplash.com/photo-1490481651871-ab68de25d43d?w=400&h=300&fit=crop&auto=format)' },
  { key: 'etiquette', icon: 'etiquette', label: '社交礼仪', bg: 'url(https://images.unsplash.com/photo-1527529482837-4698179dc6ce?w=400&h=300&fit=crop&auto=format)' },
  { key: 'pet', icon: 'pet', label: '宠物照顾', bg: 'url(https://images.unsplash.com/photo-1601758228041-f3b2795255f1?w=400&h=300&fit=crop&auto=format)' },
  { key: 'writing', icon: 'writing', label: '写作助手', bg: 'url(https://images.unsplash.com/photo-1455390582262-044cdead277a?w=400&h=300&fit=crop&auto=format)' },
  { key: 'mealplan', icon: 'mealplan', label: '食谱推荐', bg: 'url(https://images.unsplash.com/photo-1490645935967-10de6ba17061?w=400&h=300&fit=crop&auto=format)' }
]

onMounted(async () => {
  try {
    const res = await getConversations()
    conversations.value = (res.data || []).slice(0, 5)
  } catch (e) { console.error(e) }
})

function startChat(scene, label) {
  localStorage.setItem('currentScene', scene)
  localStorage.setItem('sceneLabel', label)
  router.push('/chat')
}

const svgMap = {
  cooking: '<svg viewBox="0 0 24 24" fill="none" stroke="#dc2626" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M6 13.87A4 4 0 0 1 9.5 10a4.5 4.5 0 0 1 4.5 4.5c0 1.5-.5 3-1.5 4.2A6 6 0 0 1 6 13.87Z"/><path d="M18 12a3 3 0 0 1-3-3"/><path d="M18 6v6"/><path d="M21 12h-3"/></svg>',
  shopping: '<svg viewBox="0 0 24 24" fill="none" stroke="#16a34a" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z"/><path d="M3 6h18"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>',
  repair: '<svg viewBox="0 0 24 24" fill="none" stroke="#2563eb" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>',
  housework: '<svg viewBox="0 0 24 24" fill="none" stroke="#9333ea" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>',
  health: '<svg viewBox="0 0 24 24" fill="none" stroke="#ca8a04" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>',
  fashion: '<svg viewBox="0 0 24 24" fill="none" stroke="#db2777" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M6 5v14M18 5v14M6 5l2-2h8l2 2M6 5H2v4a3 3 0 0 0 3 3h1M18 5h4v4a3 3 0 0 1-3 3h-1M6 19l2 2h8l2-2M12 7v6M9 10h6"/></svg>',
  etiquette: '<svg viewBox="0 0 24 24" fill="none" stroke="#0d9488" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v2a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2"/><path d="M17 8l4 4-4 4"/><path d="M7 8l-4 4 4 4"/><path d="M10 4l4 16"/></svg>',
  pet: '<svg viewBox="0 0 24 24" fill="none" stroke="#d97706" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="12" cy="10" rx="6" ry="4"/><path d="M12 14v4"/><path d="M8 18v2M16 18v2"/><path d="M6.5 7a2.5 2.5 0 0 1 0-5 4.5 4.5 0 0 1 3 4.5"/><path d="M17.5 7a2.5 2.5 0 0 0 0-5 4.5 4.5 0 0 0-3 4.5"/></svg>',
  writing: '<svg viewBox="0 0 24 24" fill="none" stroke="#0284c7" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>',
  mealplan: '<svg viewBox="0 0 24 24" fill="none" stroke="#d97706" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>'
}

function svgIcon(key) {
  return svgMap[key] || ''
}

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function displayConversationTitle(conv) {
  const title = (conv?.title || '').trim()
  if (!title || isBadTitle(title)) {
    return conv?.sceneLabel ? `${conv.sceneLabel}对话` : '未命名对话'
  }
  return title
}

function isBadTitle(title) {
  const questionMarks = (title.match(/\?/g) || []).length
  if (questionMarks >= 3) return true
  if (/�/.test(title)) return true
  return false
}
</script>

<style scoped>
.header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 24px 20px 12px;
  background: linear-gradient(180deg, #f0fdf4 0%, #fff 100%);
}
.header-left h2 {
  font-size: 24px;
  font-weight: 800;
  color: #111;
  margin: 0;
}
.greeting {
  font-size: 13px;
  color: #888;
  margin: 2px 0 0;
}
.header-right {
  display: flex;
  gap: 4px;
  padding-top: 4px;
}
.header-icon-btn {
  color: #555;
  padding: 6px;
  border-radius: 50%;
}
.header-icon-btn:hover { background: #f0f0f0; }

.content {
  padding: 0 20px 80px;
  background: #fff;
}

/* 快捷入口 */
.quick-chat {
  display: flex;
  align-items: center;
  gap: 14px;
  background: linear-gradient(135deg, #f0fdf4, #dcfce7);
  border: 1px solid #bbf7d0;
  border-radius: 16px;
  padding: 18px;
  margin: 16px 0 24px;
  cursor: pointer;
  transition: .15s;
}
.quick-chat:active { transform: scale(.98); }
.quick-icon {
  width: 48px;
  height: 48px;
  background: #f0fdf4;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #22c55e;
  flex-shrink: 0;
}
.quick-text { flex: 1; }
.quick-title { font-size: 15px; font-weight: 600; color: #222; }
.quick-sub { font-size: 12px; color: #999; margin-top: 2px; }
.quick-arrow { color: #ccc; font-size: 18px; }

/* 场景 */
.section-title {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 14px;
  color: #333;
  padding-left: 4px;
}
.scene-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 32px;
}
.scene-card {
  height: 100px;
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: .15s;
  background-size: cover;
  background-position: center;
  position: relative;
  overflow: hidden;
}
.scene-card::before { content: ''; position: absolute; inset: 0; background: rgba(255,255,255,0.4); border-radius: 16px; transition: all .2s; } .scene-card:active { transform: scale(.95); } .scene-card:active::before { background: rgba(255,255,255,0.25); }
.scene-icon { width: 36px; height: 36px; margin-bottom: 6px; display: flex; align-items: center; justify-content: center; position: relative; z-index: 1; filter: drop-shadow(0 2px 4px rgba(0,0,0,0.15)); }
.scene-label { font-size: 14px; font-weight: 700; color: #111; position: relative; z-index: 1; text-shadow: 0 1px 6px rgba(255,255,255,0.9); }

/* 最近问答 */
.recent-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.recent-header .section-title { margin-bottom: 0; }
.conv-item {
  display: flex;
  align-items: center;
  padding: 16px 14px;
  background: #fafcfa;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  margin-bottom: 8px;
  cursor: pointer;
}
.conv-item:active { transform: scale(.98); }
.conv-icon { font-size: 22px; margin-right: 14px; }
.conv-info { flex: 1; min-width: 0; }
.conv-title {
  font-size: 14px;
  color: #222;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-meta { font-size: 11px; color: #999; margin-top: 3px; }
.conv-arrow { color: #ccc; font-size: 14px; flex-shrink: 0; }

/* 空状态 */
.empty-state { text-align: center; padding: 40px 20px 0; }
.empty-icon { font-size: 40px; margin-bottom: 8px; }
.empty-text { font-size: 14px; color: #999; font-weight: 500; }
.empty-hint { font-size: 12px; color: #bbb; margin-top: 4px; }

/* 底部导航 */
.bottom-tabs {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: 480px;
  height: 64px;
  background: #fff;
  border-top: 1px solid #e5e7eb;
  display: flex;
  padding-bottom: 8px;
  z-index: 100;
  box-shadow: 0 -2px 12px rgba(0,0,0,0.06);
}
.tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #999;
  cursor: pointer;
  gap: 2px;
}
.tab.active { color: #22c55e; }
.tab .el-icon { font-size: 20px; }
</style>
