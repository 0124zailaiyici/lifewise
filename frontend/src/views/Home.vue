<template>
  <div class="page-container home-page">
    <!-- 顶部品牌区 -->
    <header class="topbar">
      <div class="brand-row">
        <div class="brand">
          <div class="mark">⌂</div>
          <div>
            <h1 class="brand-title">LifeWise</h1>
            <div class="subtitle">把生活问题整理成可执行步骤</div>
          </div>
        </div>
        <div class="brand-actions">
          <el-button text class="header-icon-btn" @click="$router.push('/search')">
            <el-icon :size="21"><Search /></el-icon>
          </el-button>
          <el-button text class="header-icon-btn" @click="$router.push('/profile')">
            <el-icon :size="21"><User /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 快捷提问卡片 -->
      <div class="ask-card" @click="startChat('general', '生活助手')">
        <div class="ask-main">
          <div class="ask-icon">✦</div>
          <div>
            <div class="ask-title">{{ greeting }}，今天想解决什么问题？</div>
            <div class="ask-sub">做饭、清洁、维修、购物、健康常识都可以问</div>
          </div>
        </div>
        
      </div>


    </header>

    <div class="content">
      <!-- 场景入口网格 -->
      <section>
        <div class="section-row">
          <h3 class="section-title">场景入口</h3>
          <span class="section-hint">选择对应场景开始提问</span>
        </div>
        <div class="scene-grid">
          <div v-for="s in scenes" :key="s.key" class="scene-card" :style="{ '--accent': s.accent, '--photo': 'url(' + s.photo + ')' }" @click="startChat(s.key, s.label)">
            <span v-if="s.photo && !brokenScenePhotos[s.key]" class="scene-photo" aria-hidden="true"></span>
            <div class="scene-icon" v-html="svgIcon(s.icon)"></div>
            <div class="scene-info">
              <span class="scene-label">{{ s.label }}</span>
              <small>{{ s.desc }}</small>
            </div>
          </div>
        </div>
      </section>

      <!-- 最近问答 -->
      <section class="recent-section">
        <div class="section-row">
          <h3 class="section-title">最近问答</h3>
          <button v-if="conversations.length > 0" class="section-link" @click="$router.push('/history')">查看全部</button>
        </div>
        <div v-if="conversations.length > 0">
          <div v-for="conv in conversations" :key="conv.id" class="conv-item" @click="$router.push('/chat/' + conv.id)">
            <span class="conv-icon">{{ conv.sceneIcon || '✦' }}</span>
            <div class="conv-info">
              <div class="conv-title">{{ displayConversationTitle(conv) }}</div>
              <div class="conv-meta">{{ conv.sceneLabel || '生活助手' }} · {{ formatTime(conv.createdAt) }}</div>
            </div>
            <el-icon class="conv-arrow"><ArrowRight /></el-icon>
          </div>
        </div>
        <div v-else class="empty-state">
          <div class="empty-icon">🌿</div>
          <div class="empty-text">还没有历史对话</div>
          <div class="empty-hint">从上方场景或常用问题开始，AI 会帮你整理答案</div>
        </div>
      </section>
    </div>

    <!-- 底部导航 -->
    <nav class="bottom-tabs">
      <div class="tab active"><el-icon><HomeFilled /></el-icon><span>首页</span></div>
      <div class="tab" @click="$router.push('/history')"><el-icon><Timer /></el-icon><span>历史</span></div>
      <div class="tab" @click="$router.push('/favorites')"><el-icon><Star /></el-icon><span>收藏</span></div>
      <div class="tab" @click="$router.push('/profile')"><el-icon><User /></el-icon><span>我的</span></div>
    </nav>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getConversations } from '../api'
import { Search, User, ArrowRight, HomeFilled, Timer, Star } from '@element-plus/icons-vue'
import sceneCooking from '../assets/scenes/scene-cooking.webp'
import sceneShopping from '../assets/scenes/scene-shopping.webp'
import sceneRepair from '../assets/scenes/scene-repair.webp'
import sceneHousework from '../assets/scenes/scene-housework.webp'
import sceneHealth from '../assets/scenes/scene-health.webp'
import sceneFashion from '../assets/scenes/scene-fashion.webp'
import sceneEtiquette from '../assets/scenes/scene-etiquette.webp'
import scenePet from '../assets/scenes/scene-pet.webp'
import sceneWriting from '../assets/scenes/scene-writing.webp'
import sceneMealplan from '../assets/scenes/scene-mealplan.webp'

const router = useRouter()
const conversations = ref([])
const brokenScenePhotos = ref({})

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
  { key: 'cooking', icon: 'cooking', label: '做饭助手', desc: '菜谱、火候、步骤', accent: '#8d5f3f', photo: sceneCooking },
  { key: 'shopping', icon: 'shopping', label: '购物挑选', desc: '买菜、避坑、对比', accent: '#7d8b6f', photo: sceneShopping },
  { key: 'repair', icon: 'repair', label: '修理指南', desc: '小故障先自查', accent: '#6b7280', photo: sceneRepair },
  { key: 'housework', icon: 'housework', label: '家务技巧', desc: '清洁、收纳、去渍', accent: '#a855f7', photo: sceneHousework },
  { key: 'health', icon: 'health', label: '健康常识', desc: '生活建议和提醒', accent: '#7d8b6f', photo: sceneHealth },
  { key: 'fashion', icon: 'fashion', label: '穿搭指南', desc: '配色、场合、风格', accent: '#ec4899', photo: sceneFashion },
  { key: 'etiquette', icon: 'etiquette', label: '社交礼仪', desc: '表达、送礼、沟通', accent: '#14b8a6', photo: sceneEtiquette },
  { key: 'pet', icon: 'pet', label: '宠物照顾', desc: '喂养、习惯、护理', accent: '#f59e0b', photo: scenePet },
  { key: 'writing', icon: 'writing', label: '写作助手', desc: '文案、润色、回复', accent: '#0ea5e9', photo: sceneWriting },
  { key: 'mealplan', icon: 'mealplan', label: '食谱推荐', desc: '一周菜单、营养搭配', accent: '#fb923c', photo: sceneMealplan }
]

onMounted(async () => {
  preloadScenePhotos()
  try {
    const res = await getConversations()
    conversations.value = (res.data || []).slice(0, 5)
  } catch (e) {
    console.error(e)
  }
})

function preloadScenePhotos() {
  scenes.forEach(scene => {
    if (!scene.photo) return
    const img = new Image()
    img.onerror = () => {
      brokenScenePhotos.value = { ...brokenScenePhotos.value, [scene.key]: true }
    }
    img.src = scene.photo
  })
}

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
  if (!title || title.includes('?')) {
    return conv?.sceneLabel ? (conv.sceneLabel + '对话') : '未命名对话'
  }
  return title
}
</script>

<style scoped>
/* ===== 顶部品牌区 ===== */
.topbar {
  padding: 18px 18px 8px;
  background: var(--paper);
}
.brand-row {
  display: flex; align-items: center; justify-content: space-between;
}
.brand { display: flex; align-items: center; gap: 10px; }
.mark {
  width: 36px; height: 36px; border-radius: 14px;
  display: grid; place-items: center;
  background: linear-gradient(145deg, #9b714f, #6f4a31);
  box-shadow: var(--small-shadow), inset 0 1px 0 rgba(255,255,255,.28);
  color: #fff7ea; font-size: 18px;
}
.brand-title {
  margin: 0;
  font-family: Georgia, "Times New Roman", "Songti SC", serif;
  font-weight: 600; font-size: 25px; letter-spacing: -0.04em;
  color: var(--ink);
}
.subtitle {
  margin-top: 1px; color: var(--muted); font-size: 11px;
}
.header-icon-btn { width: 36px; height: 36px; color: var(--muted); padding: 0; border-radius: 50%; background: rgba(255,250,241,.68); box-shadow: inset 0 1px 0 rgba(255,255,255,.72); border: 1px solid var(--line); }

/* ===== 快捷提问卡片 ===== */
.ask-card {
  margin-top: 14px; padding: 16px 18px;
  border-radius: var(--radius-xl);
  border: 1px solid var(--line);
  background: var(--card);
  box-shadow: var(--warm-shadow), inset 0 1px 0 rgba(255,255,255,.86);
  cursor: pointer;
}
.ask-card:active { transform: scale(.985); }
.ask-main { display: flex; align-items: center; gap: 14px; }
.ask-icon {
  width: 42px; height: 42px; border-radius: 16px;
  display: grid; place-items: center;
  background: linear-gradient(145deg, #9b714f, #6f4a31);
  color: #fff7ea; font-size: 20px; flex-shrink: 0;
}
.ask-title { font-size: 16px; font-weight: 700; color: var(--ink); }
.ask-sub { margin-top: 3px; font-size: 12px; color: var(--muted); line-height: 1.4; }
.ask-button {
  margin-top: 10px; width: fit-content; padding: 6px 12px;
  border-radius: 999px; background: rgba(141,95,63,.10);
  color: var(--accent); font-size: 12px; font-weight: 700;
}

/* ===== 常用问题 Chip 横滚 ===== */
.chips {
  display: flex; gap: 8px; overflow-x: auto;
  padding: 12px 2px 4px; scrollbar-width: none;
  -webkit-overflow-scrolling: touch;
}
.chips::-webkit-scrollbar { display: none; }
.chip {
  white-space: nowrap; flex-shrink: 0;
  border: 1px solid var(--line);
  background: rgba(255,250,241,.68);
  border-radius: 999px; padding: 8px 12px;
  color: var(--muted); font-size: 12px; cursor: pointer;
}
.chip:active { transform: scale(.96); }

/* ===== 内容区 ===== */
.content {
  padding: 4px 18px calc(80px + env(safe-area-inset-bottom, 0px));
}
.section-row {
  display: flex; align-items: center; justify-content: space-between;
  margin: 18px 0 10px;
}
.section-title { margin: 0; font-size: 15px; font-weight: 700; color: var(--ink); }
.section-hint { color: var(--soft); font-size: 11px; }
.section-link { border: none; background: transparent; color: var(--accent); font-size: 12px; font-weight: 600; cursor: pointer; padding: 4px; }

/* ===== 场景网格 ===== */
.scene-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 28px; }
.scene-card {
  position: relative; overflow: hidden; min-height: 82px;
  border-radius: 17px;
  background: linear-gradient(135deg, color-mix(in srgb, var(--accent) 10%, var(--card-solid)), var(--card-solid));
  border: 1px solid var(--line);
  padding: 12px; display: flex; align-items: center; gap: 10px; cursor: pointer;
}
.scene-card:active { transform: scale(.97); }
.scene-photo {
  position: absolute; right: 0; bottom: 0; width: 92px; height: 78px;
  background: var(--photo) center/cover no-repeat;
  opacity: .30; filter: saturate(.9) contrast(.96);
  pointer-events: none; z-index: 0;
}
.scene-photo::before {
  content: ""; position: absolute; inset: 0;
  background: linear-gradient(120deg, rgba(255,255,255,.96) 0%, rgba(255,255,255,.72) 42%, rgba(255,255,255,.12) 100%);
}
.scene-card::after {
  content: ""; position: absolute; right: -22px; bottom: -34px;
  width: 110px; height: 110px; border-radius: 50%;
  background: var(--accent); opacity: .045; pointer-events: none; z-index: 0;
}
.scene-icon {
  position: relative; z-index: 1; width: 38px; height: 38px; border-radius: 14px;
  background: color-mix(in srgb, var(--accent) 12%, var(--card-solid));
  color: var(--accent); display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.scene-icon :deep(svg) { width: 22px; height: 22px; }
.scene-info { position: relative; z-index: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.scene-label { font-size: 14px; font-weight: 700; color: var(--ink); }
.scene-info small { color: var(--soft); font-size: 10px; line-height: 1.3; }

/* ===== 最近问答 ===== */
.conv-item {
  display: flex; align-items: center; padding: 14px;
  border: 1px solid var(--line); border-radius: var(--radius-md);
  background: var(--card); margin-bottom: 8px; cursor: pointer;
}
.conv-item:active { transform: scale(.985); }
.conv-icon { width: 32px; height: 32px; border-radius: 12px; background: rgba(141,95,63,.08); display: flex; align-items: center; justify-content: center; font-size: 18px; margin-right: 12px; flex-shrink: 0; }
.conv-info { flex: 1; min-width: 0; }
.conv-title { font-size: 14px; color: var(--ink); font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conv-meta { font-size: 11px; color: var(--muted); margin-top: 3px; }
.conv-arrow { color: var(--soft); font-size: 14px; flex-shrink: 0; }

.empty-state { text-align: center; padding: 40px 20px 0; }
.empty-icon { font-size: 40px; margin-bottom: 8px; }
.empty-text { font-size: 14px; color: var(--muted); font-weight: 600; }
.empty-hint { font-size: 12px; color: var(--soft); margin-top: 5px; line-height: 1.5; }

/* ===== 底部导航 ===== */
.bottom-tabs {
  position: fixed; bottom: 0; left: 50%; transform: translateX(-50%);
  width: 100%; max-width: 480px; height: calc(64px + env(safe-area-inset-bottom, 0px));
  background: var(--card-solid); border-top: 1px solid var(--line);
  display: flex; padding-bottom: calc(8px + env(safe-area-inset-bottom, 0px));
  z-index: 100; box-shadow: 0 -2px 12px rgba(0,0,0,0.04);
}
.tab { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 11px; color: var(--muted); cursor: pointer; gap: 2px; }
.tab.active { color: var(--accent); }
.tab .el-icon { font-size: 20px; }
</style>