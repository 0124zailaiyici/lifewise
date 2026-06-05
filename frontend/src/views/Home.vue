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
        <div v-for="s in scenes" :key="s.key" class="scene-card" :style="{ background: s.bg }"
             @click="startChat(s.key, s.label)">
          <span class="scene-icon">{{ s.icon }}</span>
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
            <div class="conv-title">{{ conv.title }}</div>
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
  { key: 'cooking', icon: '🍳', label: '做饭助手', bg: 'url(https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=400&h=300&fit=crop&auto=format)' },
  { key: 'shopping', icon: '🛒', label: '买菜指南', bg: 'url(https://images.unsplash.com/photo-1488459716781-31db52582fe9?w=400&h=300&fit=crop&auto=format)' },
  { key: 'repair', icon: '🔧', label: '修理指南', bg: 'url(https://images.unsplash.com/photo-1621905251189-08b45d6a269e?w=400&h=300&fit=crop&auto=format)' },
  { key: 'housework', icon: '🏠', label: '家务技巧', bg: 'url(https://images.unsplash.com/photo-1556912167-f556f1e39f2e?w=400&h=300&fit=crop&auto=format)' },
  { key: 'health', icon: '🌞', label: '健康常识', bg: 'url(https://images.unsplash.com/photo-1544367567-0f2fcb009e0b?w=400&h=300&fit=crop&auto=format)' },
  { key: 'fashion', icon: '👔', label: '穿搭指南', bg: 'url(https://images.unsplash.com/photo-1445205170230-053b83016050?w=400&h=300&fit=crop&auto=format)' },
  { key: 'etiquette', icon: '🎂', label: '社交礼仪', bg: 'url(https://images.unsplash.com/photo-1527529482837-4698179dc6ce?w=400&h=300&fit=crop&auto=format)' },
  { key: 'pet', icon: '🐥', label: '宠物照顾', bg: 'url(https://images.unsplash.com/photo-1601758228041-f3b2795255f1?w=400&h=300&fit=crop&auto=format)' },
  { key: 'writing', icon: '✍️', label: '写作助手', bg: 'url(https://images.unsplash.com/photo-1517842645767-c639042777db?w=400&h=300&fit=crop&auto=format)' },
  { key: 'mealplan', icon: '📮', label: '食谱推荐', bg: 'url(https://images.unsplash.com/photo-1490645935967-10de6ba17061?w=400&h=300&fit=crop&auto=format)' }
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

function formatTime(t) {
  if (!t) return ''
  return new Date(t).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 20px 20px 8px;
  background: #fff;
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
}

/* 快捷入口 */
.quick-chat {
  display: flex;
  align-items: center;
  gap: 14px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 16px;
  padding: 18px;
  margin: 16px 0 24px;
  cursor: pointer;
  transition: .15s;
}
.quick-chat:active { transform: scale(.98); border-color: #22c55e; }
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
  color: #222;
}
.scene-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 28px;
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
.scene-card::before { content: ''; position: absolute; inset: 0; background: linear-gradient(180deg, rgba(255,255,255,0.35) 0%, rgba(255,255,255,0.75) 100%); border-radius: 16px; transition: all .2s; } .scene-card:active { transform: scale(.95); } .scene-card:active::before { background: linear-gradient(180deg, rgba(255,255,255,0.2) 0%, rgba(255,255,255,0.6) 100%); }
.scene-icon { font-size: 34px; margin-bottom: 6px; position: relative; z-index: 1; filter: drop-shadow(0 2px 4px rgba(0,0,0,0.1)); }
.scene-label { font-size: 15px; font-weight: 700; color: #1a1a1a; position: relative; z-index: 1; text-shadow: 0 1px 8px rgba(255,255,255,0.95); letter-spacing: 0.5px; }

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
  background: #fff;
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
  height: 60px;
  background: #fff;
  border-top: 1px solid #f0f0f0;
  display: flex;
  padding-bottom: 4px;
  z-index: 100;
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
