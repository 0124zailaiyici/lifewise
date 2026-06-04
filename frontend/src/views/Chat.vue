<template>
  <div class="page-container chat-page">
    <div class="chat-header">
      <el-button text @click="goBack" class="back-btn">← 返回</el-button>
      <span class="header-title">{{ currentLabel }}</span>
      <div style="width:50px"></div>
    </div>

    <div class="messages" ref="msgBox">
      <div v-if="messages.length === 0 && !loading" class="welcome">
        <div class="welcome-icon">🌿</div>
        <div class="welcome-text">问我关于{{ currentLabel }}的问题吧</div>
      </div>

      <div v-for="(msg, i) in messages" :key="i" :class="'msg msg-' + msg.role">
        <div class="bubble">
          <!-- 正在打字的消息：显示逐字内容 -->
          <div v-if="msg._typing"><span v-html="msg._displayHtml"></span><span class="cursor">|</span></div>
          <!-- 已完成的消息：直接渲染 -->
          <div v-else v-html="renderContent(msg.content)"></div>
        </div>
        <div v-if="msg.role === 'assistant' && !msg._typing" class="msg-actions">
          <el-button text size="small" @click="copyMsg(i)">📋 复制</el-button>
          <el-button text size="small" @click="toggleFavorite(i)" :type="msg._faved ? 'warning' : 'default'">
            {{ msg._faved ? '⭐ 已收藏' : '☆ 收藏' }}
          </el-button>
        </div>
      </div>

      <div v-if="loading && !currentTyping" class="msg msg-assistant">
        <div class="bubble thinking">
          <span class="dot">.</span><span class="dot">.</span><span class="dot">.</span>
        </div>
      </div>
    </div>

    <div class="input-area">
      <el-input v-model="inputText" ref="inputRef" placeholder="输入你的问题..." size="large"
                @keyup.enter="send" :disabled="loading" clearable />
      <el-button type="success" :icon="Promotion" circle @click="send" :disabled="loading || !inputText.trim()" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { sendChat, getConversation, addFavorite, removeFavorite } from '../api'
import { Promotion } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const msgBox = ref(null)
const inputRef = ref(null)
const messages = ref([])
const inputText = ref('')
const loading = ref(false)
const convId = ref(null)
const currentScene = ref(localStorage.getItem('currentScene') || '')
const currentLabel = ref(localStorage.getItem('sceneLabel') || 'AI 助手')
const currentTyping = ref(false)

onMounted(async () => {
  await nextTick()
  inputRef.value?.focus()

  if (route.params.id) {
    convId.value = route.params.id
    try {
      const res = await getConversation(route.params.id)
      const conv = res.data
      if (conv) {
        messages.value = conv.messages || []
        currentLabel.value = conv.sceneLabel || 'AI 助手'
        currentScene.value = conv.scene || ''
      }
      scrollBottom()
    } catch (e) {
      ElMessage.error('加载对话失败')
    }
  }
})

async function send() {
  const text = inputText.value.trim()
  if (!text || loading.value) return
  if (currentTyping.value) return // 打字中不允许新消息

  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  loading.value = true
  scrollBottom()

  try {
    const res = await sendChat(text, currentScene.value, convId.value)
    const reply = res.data
    const fullContent = reply.content

    // 创建一条空消息，开始打字
    const msg = {
      role: 'assistant',
      content: fullContent,
      _id: reply.id,
      _convId: reply.conversationId,
      _faved: false,
      _typing: true,
      _displayHtml: ''
    }
    messages.value.push(msg)
    loading.value = false
    currentTyping.value = true

    if (!convId.value && reply.conversationId) {
      convId.value = reply.conversationId
    }

    // 通过数组索引获取响应式引用
    const msgIdx = messages.value.length - 1

    // 打字机效果
    const fullHtml = renderContent(fullContent)
    let charIdx = 0
    const speed = 50          // 50ms tick
    const batchSize = 5       // 每 tick 5 个字，减少更新频率

    const timer = setInterval(() => {
      const m = messages.value[msgIdx]
      if (!m) { clearInterval(timer); return }
      charIdx += batchSize
      if (charIdx >= fullHtml.length) {
        clearInterval(timer)
        m._typing = false
        m._displayHtml = fullHtml
        currentTyping.value = false
        scrollBottom()
        return
      }
      m._displayHtml = fullHtml.substring(0, charIdx)
      if (charIdx % 15 < batchSize) scrollBottom() // 每 3 tick 滚动一次
    }, speed)

  } catch (e) {
    loading.value = false
    currentTyping.value = false
    ElMessage.error('请求失败，请稍后再试')
    messages.value.push({ role: 'assistant', content: '抱歉，请求出错了，请稍后再试。' })
  }
  scrollBottom()
}

function scrollBottom() {
  nextTick(() => {
    if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
  })
}

function goBack() { router.push('/home') }

function copyMsg(index) {
  const msg = messages.value[index]
  if (!msg) return
  let text = msg.content
  try {
    const obj = JSON.parse(text)
    if (obj && typeof obj === 'object') text = formatObjForCopy(obj)
  } catch (e) {}
  doCopy(text)
}

function formatObjForCopy(obj) {
  let parts = []
  if (obj.title) parts.push('【' + obj.title + '】')
  if (obj.品类) parts.push('【' + obj.品类 + '】')
  if (obj.ingredients) { parts.push(''); parts.push('食材：'); obj.ingredients.forEach(i => parts.push('  ' + i)) }
  if (obj.steps) { parts.push(''); parts.push('步骤：'); obj.steps.forEach(s => parts.push('  ' + s)) }
  if (obj.挑选步骤) { obj.挑选步骤.forEach(s => { parts.push(''); parts.push(s.步骤名称 || ''); if (s.具体操作) parts.push('  ' + s.具体操作) }) }
  if (obj.tips) { parts.push(''); const tips = Array.isArray(obj.tips) ? obj.tips : [obj.tips]; tips.forEach(t => parts.push('提示：' + t)) }
  if (obj.总结口诀) parts.push(''); parts.push(obj.总结口诀)
  if (obj.常见误区) { parts.push(''); parts.push('误区：'); obj.常见误区.forEach(m => parts.push('  ' + m)) }
  return parts.join('\n')
}

function doCopy(text) {
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(() => ElMessage.success('已复制')).catch(() => fallbackCopy(text))
    } else { fallbackCopy(text) }
  } catch { fallbackCopy(text) }
}
function fallbackCopy(text) {
  try {
    const ta = document.createElement('textarea')
    ta.value = text; ta.style.cssText = 'position:fixed;top:0;left:0;width:100px;height:100px;opacity:0'
    document.body.appendChild(ta); ta.focus(); ta.select()
    const ok = document.execCommand('copy'); document.body.removeChild(ta)
    if (ok) ElMessage.success('已复制'); else ElMessage.error('复制失败')
  } catch { ElMessage.error('复制失败') }
}

async function toggleFavorite(index) {
  const msg = messages.value[index]
  if (!msg || !msg._id) { ElMessage.warning('无法收藏该消息'); return }
  try {
    if (msg._faved) { await removeFavorite(msg._id); msg._faved = false; ElMessage.success('已取消收藏') }
    else { await addFavorite(msg._id, ''); msg._faved = true; ElMessage.success('已收藏') }
  } catch { ElMessage.error('操作失败') }
}

function renderContent(content) {
  if (!content) return ''
  try { const data = JSON.parse(content); if (typeof data === 'object') return renderStructured(data) } catch (e) {}
  return content.replace(/\n/g, '<br>')
}

function renderStructured(data) {
  if (data.title && data.ingredients) {
    let html = `<div class="rc-card"><h4>${data.title}</h4>`
    if (data.difficulty || data.time || data.servings) { html += `<div class="rc-tags">`; if (data.difficulty) html += data.difficulty; if (data.time) html += ` · ⏱️ ${data.time}`; if (data.servings) html += ` · ${data.servings}`; html += `</div>` }
    html += `<div class="rc-sec">🥘 食材</div>`; if (Array.isArray(data.ingredients)) data.ingredients.forEach(i => { html += `<div class="rc-item">· ${i}</div>` })
    html += `<div class="rc-sec">👨‍🍳 步骤</div>`; if (Array.isArray(data.steps)) data.steps.forEach(s => { html += `<div class="rc-item">${s}</div>` })
    if (data.tips) { if (Array.isArray(data.tips)) { html += `<div class="rc-tip">${data.tips.map(t => '💡 ' + t).join('<br>')}</div>` } else { html += `<div class="rc-tip">💡 ${data.tips}</div>` } }
    html += `</div>`; return html
  }
  if (data.挑选步骤 || data.品类) {
    let html = `<div class="rc-card">`; if (data.品类) html += `<h4>🛒 ${data.品类}挑选指南</h4>`
    if (data.挑选步骤) { data.挑选步骤.forEach(s => { html += `<div class="rc-guide-step"><div class="rc-guide-title">${s.步骤名称 || ''}</div><div class="rc-item">${s.具体操作 || ''}</div></div>` }) }
    if (data.常见误区) { html += `<div class="rc-sec">⚠️ 常见误区</div>`; data.常见误区.forEach(m => { html += `<div class="rc-item">· ${m}</div>` }) }
    if (data.总结口诀) html += `<div class="rc-tip">📝 ${data.总结口诀}</div>`; html += `</div>`; return html
  }
  let html = `<div class="rc-card">`
  for (const [key, val] of Object.entries(data)) {
    if (Array.isArray(val)) { html += `<div class="rc-sec">${key}</div>`; val.forEach(v => { if (typeof v === 'object') Object.entries(v).forEach(([k2, v2]) => { html += `<div class="rc-item"><strong>${k2}</strong>：${v2}</div>` }); else html += `<div class="rc-item">· ${v}</div>` }) }
    else if (typeof val === 'object') { html += `<div class="rc-sec">${key}</div>`; Object.entries(val).forEach(([k2, v2]) => { html += `<div class="rc-item"><strong>${k2}</strong>：${v2}</div>` }) }
    else if (key !== 'title' && key !== 'time' && key !== 'difficulty' && key !== 'servings') { html += `<div class="rc-sec">${key}</div><div class="rc-item">${val}</div>` }
  }
  html += `</div>`; return html
}
</script>

<style scoped>
.chat-page { display: flex; flex-direction: column; height: 100vh; background: #fafafa; }
.chat-header { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid #f0f0f0; background: #fff; }
.header-title { font-size: 16px; font-weight: 600; color: #1a1a1a; }
.back-btn { font-size: 14px; }
.messages { flex: 1; overflow-y: auto; padding: 20px 16px 8px; }
.welcome { text-align: center; padding: 80px 20px; color: #ccc; }
.welcome-icon { font-size: 56px; margin-bottom: 16px; }
.welcome-text { font-size: 15px; }
.msg { margin-bottom: 24px; }
.msg-user { text-align: right; display: flex; flex-direction: column; align-items: flex-end; }
.msg-user .bubble { display: inline-block; background: linear-gradient(135deg, #22c55e, #16a34a); color: #fff; padding: 12px 18px; border-radius: 18px 18px 4px 18px; font-size: 14px; max-width: 85%; text-align: left; line-height: 1.6; word-break: break-word; box-shadow: 0 2px 8px rgba(34,197,94,.15); }
.msg-assistant .bubble { display: inline-block; background: #fff; color: #333; padding: 14px 18px; border-radius: 18px 18px 18px 4px; font-size: 14px; max-width: 98%; text-align: left; line-height: 1.7; word-break: break-word; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.msg-actions { display: flex; gap: 4px; margin-top: 6px; padding-left: 4px; }

.thinking { color: #999 !important; font-size: 24px !important; letter-spacing: 4px; }
.dot { animation: blink 1.4s infinite both; }
.dot:nth-child(2) { animation-delay: .2s; }
.dot:nth-child(3) { animation-delay: .4s; }
@keyframes blink { 0%,80%,100% { opacity: 0; } 40% { opacity: 1; } }

.cursor { animation: cursorBlink 0.8s infinite; color: #22c55e; font-weight: bold; display: inline-block; }
@keyframes cursorBlink { 0%,50% { opacity: 1; } 51%,100% { opacity: 0; } }

.input-area { display: flex; align-items: center; gap: 8px; padding: 12px 16px; border-top: 1px solid #e0e0e0; background: #fff; box-shadow: 0 -2px 12px rgba(0,0,0,.05); }

.rc-card { background: #fff; border: 1px solid #e0e0e0; border-radius: 12px; padding: 14px; margin: 4px 0; }
.rc-card h4 { font-size: 16px; margin-bottom: 4px; }
.rc-card .rc-tags { font-size: 12px; color: #666; margin-bottom: 10px; }
.rc-sec { font-weight: 600; font-size: 13px; margin: 10px 0 4px; color: #444; }
.rc-item { font-size: 13px; color: #333; margin: 5px 0; padding-left: 2px; line-height: 1.6; }
.rc-tip { background: #fefce8; border-radius: 8px; padding: 10px; font-size: 12px; color: #a16207; margin-top: 10px; line-height: 1.6; }
.rc-guide-step { margin: 8px 0; }
.rc-guide-title { font-weight: 600; font-size: 13px; color: #444; margin-bottom: 4px; }
</style>
