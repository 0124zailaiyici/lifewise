<template>
  <div class="page-container">
    <div class="home-hero">
      <div class="hero-top">
        <div>
          <p class="eyebrow">LifeWise</p>
          <h2>{{ greeting }}</h2>
          <p class="hero-sub">生活里的小问题，直接问就行</p>
        </div>
        <div class="hero-actions">
          <el-button text class="header-icon-btn" @click="$router.push('/search')">
            <el-icon :size="21"><Search /></el-icon>
          </el-button>
          <el-button text class="header-icon-btn" @click="$router.push('/profile')">
            <el-icon :size="21"><User /></el-icon>
          </el-button>
        </div>
      </div>

      <div class="ask-card" @click="startChat('general', '生活助手')">
        <div class="ask-main">
          <div class="ask-icon"><el-icon :size="24"><ChatDotSquare /></el-icon></div>
          <div>
            <div class="ask-title">今天想解决什么问题？</div>
            <div class="ask-sub">做饭、清洁、维修、购物、健康常识都可以问</div>
          </div>
        </div>
        <div class="ask-button">去提问</div>
      </div>
    </div>

    <div class="content">
      <section class="quick-section">
        <div class="section-row">
          <h3 class="section-title">常用问题</h3>
          <button class="link-btn" @click="$router.push('/chat')">自由提问</button>
        </div>
        <div class="prompt-list">
          <button v-for="p in quickPrompts" :key="p.text" class="prompt-chip" @click="askPrompt(p)">
            <span>{{ p.icon }}</span>{{ p.text }}
          </button>
        </div>
      </section>

      <section>
        <div class="section-row">
          <h3 class="section-title">场景入口</h3>
          <span class="section-hint">选择后会带入对应场景</span>
        </div>
        <div class="scene-grid">
          <div v-for="s in scenes" :key="s.key" class="scene-card" :style="{ '--accent': s.accent }" @click="startChat(s.key, s.label)">
            <div class="scene-icon" v-html="svgIcon(s.icon)"></div>
            <div class="scene-info">
              <span class="scene-label">{{ s.label }}</span>
              <small>{{ s.desc }}</small>
            </div>
          </div>
        </div>
      </section>

      <section v-if="conversations.length > 0" class="recent-section">
        <div class="section-row">
          <h3 class="section-title">最近问答</h3>
          <button class="link-btn" @click="$router.push('/history')">查看全部</button>
        </div>
        <div v-for="conv in conversations" :key="conv.id" class="conv-item" @click="$router.push('/chat/' + conv.id)">
          <span class="conv-icon">{{ conv.sceneIcon || '💬' }}</span>
          <div class="conv-info">
            <div class="conv-title">{{ displayConversationTitle(conv) }}</div>
            <div class="conv-meta">{{ conv.sceneLabel || '生活助手' }} · {{ formatTime(conv.createdAt) }}</div>
          </div>
          <el-icon class="conv-arrow"><ArrowRight /></el-icon>
        </div>
      </section>

      <div v-else class="empty-state">
        <div class="empty-icon">💡</div>
        <div class="empty-text">还没有历史对话</div>
        <div class="empty-hint">从上方场景或常用问题开始，AI 会帮你整理答案</div>
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
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getConversations } from '../api'
import { Search, User, ArrowRight, ChatDotSquare, HomeFilled, Timer, Star } from '@element-plus/icons-vue'

const router = useRouter()
const conversations = ref([])

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了，注意休息'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const quickPrompts = [
  { icon: '🍳', text: '今晚吃什么', scene: 'mealplan', label: '食谱推荐' },
  { icon: '🧽', text: '衣服染色怎么办', scene: 'housework', label: '家务技巧' },
  { icon: '🛠️', text: '水龙头滴水怎么办', scene: 'repair', label: '修理指南' },
  { icon: '🛒', text: '怎么挑西瓜', scene: 'shopping', label: '买菜指南' },
  { icon: '🐱', text: '猫不爱喝水怎么办', scene: 'pet', label: '宠物照顾' }
]

const scenes = [
  { key: 'cooking', icon: 'cooking', label: '做饭助手', desc: '菜谱、火候、步骤', accent: '#ef4444' },
  { key: 'shopping', icon: 'shopping', label: '购物挑选', desc: '买菜、避坑、对比', accent: '#22c55e' },
  { key: 'repair', icon: 'repair', label: '修理指南', desc: '小故障先自查', accent: '#3b82f6' },
  { key: 'housework', icon: 'housework', label: '家务技巧', desc: '清洁、收纳、去渍', accent: '#a855f7' },
  { key: 'health', icon: 'health', label: '健康常识', desc: '生活建议和提醒', accent: '#eab308' },
  { key: 'fashion', icon: 'fashion', label: '穿搭指南', desc: '配色、场合、风格', accent: '#ec4899' },
  { key: 'etiquette', icon: 'etiquette', label: '社交礼仪', desc: '表达、送礼、沟通', accent: '#14b8a6' },
  { key: 'pet', icon: 'pet', label: '宠物照顾', desc: '喂养、习惯、护理', accent: '#f59e0b' },
  { key: 'writing', icon: 'writing', label: '写作助手', desc: '文案、润色、回复', accent: '#0ea5e9' },
  { key: 'mealplan', icon: 'mealplan', label: '食谱推荐', desc: '一周菜单、营养搭配', accent: '#fb923c' }
]

onMounted(async () => {
  try {
    const res = await getConversations()
    conversations.value = (res.data || []).slice(0, 5)
  } catch (e) {
    console.error(e)
  }
})

function startChat(scene, label) {
  localStorage.setItem('currentScene', scene)
  localStorage.setItem('sceneLabel', label)
  router.push('/chat')
}

function askPrompt(prompt) {
  localStorage.setItem('currentScene', prompt.scene)
  localStorage.setItem('sceneLabel', prompt.label)
  localStorage.setItem('pendingPrompt', prompt.text)
  router.push('/chat')
}

const svgMap = {
  cooking: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M6 13.87A4 4 0 0 1 9.5 10a4.5 4.5 0 0 1 4.5 4.5c0 1.5-.5 3-1.5 4.2A6 6 0 0 1 6 13.87Z"/><path d="M18 6v6"/><path d="M21 12h-6"/></svg>',
  shopping: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z"/><path d="M3 6h18"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>',
  repair: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/></svg>',
  housework: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M9 22V12h6v10"/></svg>',
  health: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/></svg>',
  fashion: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M6 5v14M18 5v14M6 5l2-2h8l2 2M6 19l2 2h8l2-2M12 7v6M9 10h6"/></svg>',
  etiquette: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v2a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2"/><path d="M17 8l4 4-4 4"/><path d="M7 8l-4 4 4 4"/></svg>',
  pet: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="12" cy="10" rx="6" ry="4"/><path d="M12 14v4"/><path d="M8 18v2M16 18v2"/><path d="M6.5 7a2.5 2.5 0 0 1 0-5 4.5 4.5 0 0 1 3 4.5"/></svg>',
  writing: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z"/></svg>',
  mealplan: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>'
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
  if (title.includes(String.fromCharCode(0x951f)) || title.includes(String.fromCharCode(0xfffd))) return true
  return false
}
</script>

<style scoped>
.home-hero { padding: 24px 20px 18px; background: radial-gradient(circle at 20% 0%, #dcfce7 0%, #f0fdf4 36%, #fff 76%); }
.hero-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.eyebrow { margin: 0 0 4px; font-size: 13px; font-weight: 800; color: #16a34a; letter-spacing: .02em; }
.hero-top h2 { margin: 0; font-size: 26px; font-weight: 900; color: #111827; }
.hero-sub { margin: 5px 0 0; color: #6b7280; font-size: 13px; }
.hero-actions { display: flex; gap: 4px; padding-top: 4px; }
.header-icon-btn { width: 36px; height: 36px; color: #374151; padding: 0; border-radius: 50%; background: rgba(255,255,255,.72); box-shadow: 0 4px 12px rgba(15,23,42,.06); }
.ask-card { margin-top: 18px; padding: 15px; border-radius: 20px; background: linear-gradient(135deg, #22c55e, #16a34a); color: #fff; box-shadow: 0 14px 28px rgba(34,197,94,.22); cursor: pointer; }
.ask-card:active { transform: scale(.985); }
.ask-main { display: flex; align-items: center; gap: 12px; }
.ask-icon { width: 44px; height: 44px; border-radius: 15px; background: rgba(255,255,255,.18); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.ask-title { font-size: 16px; font-weight: 900; }
.ask-sub { margin-top: 3px; font-size: 12px; opacity: .84; line-height: 1.4; }
.ask-button { margin-top: 12px; width: fit-content; padding: 6px 12px; border-radius: 999px; background: rgba(255,255,255,.18); font-size: 12px; font-weight: 800; }
.content { padding: 0 20px calc(80px + env(safe-area-inset-bottom, 0px)); background: #fff; }
.quick-section { margin-top: 8px; }
.section-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin: 18px 0 10px; }
.section-title { margin: 0; font-size: 16px; font-weight: 900; color: #111827; }
.section-hint { color: #9ca3af; font-size: 11px; }
.link-btn { border: none; background: transparent; color: #16a34a; font-size: 12px; font-weight: 800; padding: 4px; cursor: pointer; }
.prompt-list { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 3px; margin: 0 -20px; padding-left: 20px; padding-right: 20px; scrollbar-width: none; }
.prompt-list::-webkit-scrollbar { display: none; }
.prompt-chip { flex: 0 0 auto; border: 1px solid #e5f4e9; background: #fbfefc; color: #334155; border-radius: 999px; padding: 8px 12px; font-size: 12px; font-weight: 700; cursor: pointer; display: inline-flex; align-items: center; gap: 6px; }
.prompt-chip:active { transform: scale(.97); background: #ecfdf5; }
.scene-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 28px; }
.scene-card { min-height: 82px; border-radius: 17px; background: linear-gradient(135deg, color-mix(in srgb, var(--accent) 10%, #fff), #fff); border: 1px solid color-mix(in srgb, var(--accent) 22%, #e5e7eb); padding: 12px; display: flex; align-items: center; gap: 10px; cursor: pointer; box-shadow: 0 8px 18px rgba(15,23,42,.035); }
.scene-card:active { transform: scale(.97); }
.scene-icon { width: 38px; height: 38px; border-radius: 14px; background: color-mix(in srgb, var(--accent) 12%, #fff); color: var(--accent); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.scene-icon :deep(svg) { width: 22px; height: 22px; }
.scene-info { min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.scene-label { font-size: 14px; font-weight: 900; color: #111827; }
.scene-info small { color: #8a94a6; font-size: 10px; line-height: 1.3; }
.recent-section { margin-top: 2px; }
.conv-item { display: flex; align-items: center; padding: 14px; background: #fafcfa; border: 1px solid #eef2f7; border-radius: 14px; margin-bottom: 8px; cursor: pointer; }
.conv-item:active { transform: scale(.985); }
.conv-icon { width: 32px; height: 32px; border-radius: 12px; background: #ecfdf5; display: flex; align-items: center; justify-content: center; font-size: 18px; margin-right: 12px; }
.conv-info { flex: 1; min-width: 0; }
.conv-title { font-size: 14px; color: #1f2937; font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conv-meta { font-size: 11px; color: #9ca3af; margin-top: 3px; }
.conv-arrow { color: #cbd5e1; font-size: 14px; flex-shrink: 0; }
.empty-state { text-align: center; padding: 34px 20px 0; }
.empty-icon { font-size: 40px; margin-bottom: 8px; }
.empty-text { font-size: 14px; color: #6b7280; font-weight: 800; }
.empty-hint { font-size: 12px; color: #a1a1aa; margin-top: 5px; line-height: 1.5; }
.bottom-tabs { position: fixed; bottom: 0; left: 50%; transform: translateX(-50%); width: 100%; max-width: 480px; height: calc(64px + env(safe-area-inset-bottom, 0px)); background: #fff; border-top: 1px solid #e5e7eb; display: flex; padding-bottom: calc(8px + env(safe-area-inset-bottom, 0px)); z-index: 100; box-shadow: 0 -2px 12px rgba(0,0,0,0.06); }
.tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 11px; color: #999; cursor: pointer; gap: 2px; }
.tab.active { color: #22c55e; }
.tab .el-icon { font-size: 20px; }
</style>
