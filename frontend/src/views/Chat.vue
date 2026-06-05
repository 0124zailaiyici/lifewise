<template>
  <div class="page-container chat-page">
    <div class="chat-header">
      <el-button text @click="goBack" class="back-btn">← 返回</el-button>
      <span class="header-title" @click="handleRenameTitle" style="cursor:pointer">{{ currentLabel }}</span>
      <div style="width:50px"></div>
    </div>

    <div class="messages" ref="msgBox">
      <div v-if="messages.length === 0 && !loading" class="welcome">
        <div class="welcome-icon">🌿</div>
        <div class="welcome-text">问我关于{{ currentLabel }}的问题吧</div>
      </div>

      <div v-for="(msg, i) in messages" :key="msg._id || i" :class="'msg msg-' + msg.role" :data-msg-id="msg._id || ''" :data-msg-index="i">
        <div class="bubble">
          <div v-if="msg._typing">
            <span v-html="msg._displayHtml"></span><span class="cursor">|</span>
          </div>
          <div v-else>
            <div v-if="msg.imageUrl" class="msg-image" @click="previewImage(msg.imageUrl)">
              <img :src="imgUrl(msg.imageUrl)" alt="图片" loading="lazy" />
            </div>
            <div v-html="renderContent(msg.content)"></div>
          </div>
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

    <!-- 图片预览 -->
    <div v-if="previewImg" class="image-overlay" @click="previewImg = null">
      <img :src="imgUrl(previewImg)" class="preview-full" />
      <div class="preview-close" @click="previewImg = null">✕</div>
    </div>

    <div class="input-area">
      <el-button :icon="Microphone" circle size="small" @click="startVoice" :type="isListening ? 'danger' : 'default'" :disabled="loading" />
      <el-button class="upload-btn" :icon="Picture" circle size="small" @click="triggerUpload" :disabled="loading" />
      <input ref="fileInput" type="file" accept="image/*" style="display:none" @change="handleFileSelect" />
      <div v-if="pendingImage" class="pending-preview">
        <img :src="pendingImage" />
        <span class="pending-remove" @click="pendingImage = null; pendingFile = null">✕</span>
      </div>
      <el-input v-model="inputText" ref="inputRef" placeholder="输入你的问题..." size="large"
                @keyup.enter="send" :disabled="loading" clearable />
      <el-button type="success" :icon="Promotion" circle @click="send" :disabled="loading || !inputText.trim() && !pendingFile" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getConversation, sendChat, addFavorite, removeFavorite, uploadImage } from '../api'
import { ArrowLeft, DocumentCopy, Microphone, Picture, Promotion } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const msgBox = ref(null)
const inputRef = ref(null)
const inputText = ref('')
const fileInput = ref(null)
const pendingFile = ref(null)
const pendingImage = ref(null)
const loading = ref(false)
const messages = ref([])
const currentTyping = ref(false)
const previewImg = ref(null)
const isListening = ref(false)

const currentLabel = computed(() => localStorage.getItem('sceneLabel') || '生活常识')

onMounted(async () => {
  msgBox.value?.addEventListener('click', handleFollowUpClick)
  await nextTick()
  inputRef.value?.focus()
  if (route.params.id) await loadConversation(route.params.id)
})
onUnmounted(() => msgBox.value?.removeEventListener('click', handleFollowUpClick))

async function loadConversation(id) {
  loading.value = true
  try {
    const res = await getConversation(id)
    const data = res.data
    if (data?.scene) {
      localStorage.setItem('currentScene', data.scene)
      localStorage.setItem('sceneLabel', data.sceneLabel || '')
    }
    messages.value = (data?.messages || []).map((m, i) => ({
      _id: m.id || 'msg-' + i, role: m.role, content: m.content || '', imageUrl: m.imageUrl || '',
      _typing: false, _displayHtml: '', _faved: m.faved || false
    }))
    await nextTick(); scrollBottom()
  } catch { ElMessage.error('加载对话失败') }
  finally { loading.value = false }
}

async function send() {
  const msg = inputText.value.trim()
  if (!msg && !pendingFile.value) return

  const scene = localStorage.getItem('currentScene') || 'other'
  const convId = route.params.id ? Number(route.params.id) : null
  let imageUrl = ''

  if (pendingFile.value) {
    try {
      const uploadRes = await uploadImage(pendingFile.value)
      imageUrl = uploadRes.data?.url || ''
      pendingFile.value = null; pendingImage.value = null
    } catch { ElMessage.error('图片上传失败'); return }
  }

  messages.value.push({ _id: 'user-' + Date.now(), role: 'user', content: msg, imageUrl, _typing: false, _displayHtml: '', _faved: false })
  inputText.value = ''
  await nextTick(); scrollBottom()

  loading.value = true; currentTyping.value = true
  const aiIdx = messages.value.length
  messages.value.push({ _id: 'ai-' + Date.now(), role: 'assistant', content: '', imageUrl: '', _typing: true, _displayHtml: '', _faved: false })

  try {
    const res = await sendChat(msg, scene, convId, imageUrl)
    const fullContent = typeof res.data === 'string' ? res.data : (res.data?.answer || JSON.stringify(res.data))
    const m = messages.value[aiIdx]
    if (m) {
      m._typing = false; m.content = fullContent
      m._displayHtml = renderContent(fullContent)
      currentTyping.value = false; scrollBottom()
    }
  } catch {
    const m = messages.value[aiIdx]
    if (m) { m._typing = false; m._displayHtml = '<div style="color:#ef4444;padding:8px">请求出错了，请稍后再试</div>'; currentTyping.value = false }
  } finally { loading.value = false }
}

function copyMsg(i) {
  const text = messages.value[i]?.content || ''
  navigator.clipboard.writeText(text).then(() => ElMessage.success('已复制')).catch(() => {
    const ta = document.createElement('textarea'); ta.value = text
    document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta)
    ElMessage.success('已复制')
  })
}

async function toggleFavorite(i) {
  const msg = messages.value[i]; if (!msg) return
  try {
    if (msg._faved) { await removeFavorite(msg._id); msg._faved = false; ElMessage.success('已取消收藏') }
    else { await addFavorite(msg._id, ''); msg._faved = true; ElMessage.success('已收藏') }
  } catch { ElMessage.error('操作失败') }
}

function handleFollowUpClick(e) {
  const chip = e.target.closest('.rc-followup-chip')
  if (chip) { inputText.value = chip.textContent; send() }
}

function triggerUpload() { fileInput.value?.click() }
function handleFileSelect(e) {
  const file = e.target.files?.[0]; if (!file) return
  pendingFile.value = file
  const reader = new FileReader()
  reader.onload = (ev) => { pendingImage.value = ev.target.result }
  reader.readAsDataURL(file); e.target.value = ''
}
function imgUrl(url) {
  if (!url) return ''
  if (url.startsWith('http') || url.startsWith('data:')) return url
  return 'http://localhost:8080' + url
}
function startVoice() {
  if (!('webkitSpeechRecognition' in window)) { ElMessage.warning('当前浏览器不支持语音识别'); return }
  const recognition = new (window.SpeechRecognition || window.webkitSpeechRecognition)()
  recognition.lang = 'zh-CN'; recognition.continuous = false; recognition.interimResults = false
  isListening.value = true
  recognition.onresult = (e) => { inputText.value = e.results[0][0].transcript; isListening.value = false }
  recognition.onerror = () => isListening.value = false
  recognition.onend = () => isListening.value = false
  recognition.start()
}
async function handleRenameTitle() {
  const { value } = await ElMessageBox.prompt('输入新标题', '重命名对话', { inputValue: currentLabel.value, inputValidator: v => v?.trim() ? true : '标题不能为空' })
  if (value?.trim()) localStorage.setItem('sceneLabel', value.trim())
}
function goBack() { router.back() }
function scrollBottom() { nextTick(() => { if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight }) }

// ===== 渲染 =====
function renderContent(content) {
  if (!content) return ''
  const data = tryParseJsonSafe(content)
  if (data) return renderStructured(data)
  return renderMarkdown(content)
}
function renderMarkdown(text) {
  let html = (text || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
    .replace(/^### (.+)$/gm, '<h4>$1</h4>')
    .replace(/^## (.+)$/gm, '<h3>$1</h3>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/^[-*] (.+)$/gm, '<li>$1</li>')
    .replace(/^\d+\.\s+(.+)$/gm, '<li>$1</li>')
    .replace(/\n/g, '<br>')
    .replace(/(<li>.*?<\/li>)(<br>\s*)(?=<li>)/g, '$1\n')
    .replace(/((?:<li>.*<\/li>\n?)+)/g, '<ul>$1</ul>')
  return '<div class="md-content">' + html + '</div>'
}
function tryParseJsonSafe(text) {
  try { const obj = JSON.parse(text); if (obj && typeof obj === 'object') return obj } catch {}
  const m = text.match(/\{[\s\S]*\}/)
  if (m) { try { const obj = JSON.parse(m[0]); if (obj && typeof obj === 'object') return obj } catch {} }
  return null
}
function renderStructured(data) {
  const parts = []
  if (data.title) {
    parts.push(`<h4 class="rc-card-title">${esc(data.title)}</h4>`)
    let tags = ''
    if (data.difficulty) tags += `📊 ${esc(data.difficulty)}`
    if (data.time) tags += ` · ⏱️ ${esc(data.time)}`
    if (data.servings) tags += ` · ${esc(data.servings)}`
    if (tags) parts.push(`<div class="rc-tags">${tags}</div>`)
  } else if (data.品类) parts.push(`<h4 class="rc-card-title">🛒 ${esc(data.品类)}</h4>`)
  else if (data.problem) parts.push(`<h4 class="rc-card-title">🔧 ${esc(data.problem)}</h4>`)
  else if (data.question) parts.push(`<h4 class="rc-card-title">💬 ${esc(data.question)}</h4>`)
  else if (data.occasion) parts.push(`<h4 class="rc-card-title">📍 ${esc(data.occasion)}</h4>`)

  if (data.ingredients) {
    parts.push('<div class="rc-sec">🥘 食材</div>')
    data.ingredients.forEach(item => {
      const note = item.note ? `<span class="rc-note">💡 ${esc(item.note)}</span>` : ''
      parts.push(`<div class="rc-item">· ${esc(item.name)}${item.amount ? ' ' + esc(item.amount) : ''}${note}</div>`)
    })
  }
  if (data.steps) {
    parts.push('<div class="rc-sec">👨‍🍳 步骤</div>')
    data.steps.forEach(s => {
      const tip = s.tip ? `<span class="rc-note">💡 ${esc(s.tip)}</span>` : ''
      const warning = s.warning ? `<span class="rc-warning">⚠️ ${esc(s.warning)}</span>` : ''
      parts.push(`<div class="rc-item">${s.step ? `<strong>步骤 ${s.step}：</strong>` : ''}${esc(s.action || s)}${tip}${warning}</div>`)
    })
  }
  if (data.selection_steps) {
    parts.push('<div class="rc-sec">🔍 挑选步骤</div>')
    data.selection_steps.forEach(s => parts.push(`<div class="rc-item"><strong>${esc(s.step_name)}：</strong>${esc(s.action)}</div>`))
  }
  if (data.tools) {
    parts.push('<div class="rc-sec">🔧 所需工具</div>')
    data.tools.forEach(t => parts.push(`<div class="rc-item">· ${esc(t)}</div>`))
  }
  if (data.tips) {
    parts.push('<div class="rc-sec">💡 小贴士</div>')
    data.tips.forEach(t => parts.push(`<div class="rc-item">· ${esc(t)}</div>`))
  }
  if (data.key_point) parts.push(`<div class="rc-tip">🔥 ${esc(data.key_point)}</div>`)
  if (data.safety_tip) parts.push(`<div class="rc-tip" style="color:#dc2626">⚠️ ${esc(data.safety_tip)}</div>`)
  if (data.answer) parts.push(`<div class="rc-item" style="margin-top:8px">${esc(data.answer).replace(/\n/g, '<br>')}</div>`)
  if (data.suggestions) {
    parts.push('<div class="rc-sec">💡 建议</div>')
    data.suggestions.forEach(s => parts.push(`<div class="rc-item"><strong>${esc(s.item)}</strong>：${esc(s.detail)}</div>`))
  }
  let recQ = data.followUps || []
  if (!recQ.length) {
    const kw = (data.title || data.question || data.problem || '').replace(/[、，。]/g, ' ').trim()
    if (kw && kw.length > 1) recQ = [`${kw}的做法`, `${kw}需要什么材料`, `${kw}有什么技巧`]
  }
  if (recQ.length) {
    parts.push(`<div class="rc-followups"><div class="rc-followup-title">💡 你可能还想问</div>${recQ.map(q => `<span class="rc-followup-chip">${esc(q)}</span>`).join(' ')}</div>`)
  }
  return '<div class="rc-card">' + parts.join('') + '</div>'
}
function esc(s) { if (typeof s !== 'string') return ''; return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;') }
</script>

<style scoped>
.chat-header { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid #f0f0f0; background: #fff; }
.header-title { font-size: 16px; font-weight: 600; color: #1a1a1a; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 60%; text-align: center; }
.back-btn { font-size: 14px; }

.messages { flex: 1; overflow-y: auto; padding: 20px 16px 8px; height: calc(100vh - 130px); }
.welcome { text-align: center; padding: 60px 20px; }
.welcome-icon { font-size: 56px; margin-bottom: 16px; }
.welcome-text { font-size: 15px; color: #999; }

.msg { margin-bottom: 20px; }
.msg-user { text-align: right; display: flex; flex-direction: column; align-items: flex-end; }
.msg-user .bubble { display: inline-block; background: linear-gradient(135deg, #22c55e, #16a34a); color: #fff; padding: 12px 18px; border-radius: 18px 18px 4px 18px; font-size: 14px; max-width: 85%; text-align: left; line-height: 1.6; word-break: break-word; box-shadow: 0 2px 8px rgba(34,197,94,.15); }
.msg-assistant .bubble { display: inline-block; background: #fff; border: 1px solid #eee; color: #333; padding: 14px 18px; border-radius: 18px 18px 18px 4px; font-size: 14px; max-width: 98%; text-align: left; line-height: 1.7; word-break: break-word; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.msg-actions { display: flex; gap: 4px; margin-top: 6px; padding-left: 4px; opacity: .6; }
.msg-actions:hover { opacity: 1; }

.thinking { color: #999 !important; font-size: 24px !important; letter-spacing: 4px; }
.dot { animation: blink 1.4s infinite both; }
.dot:nth-child(2) { animation-delay: .2s; }
.dot:nth-child(3) { animation-delay: .4s; }
@keyframes blink { 0%,80%,100% { opacity: 0; } 40% { opacity: 1; } }
.cursor { animation: cursorBlink 0.8s infinite; color: #22c55e; font-weight: bold; }
@keyframes cursorBlink { 0%,50% { opacity: 1; } 51%,100% { opacity: 0; } }

.input-area { display: flex; align-items: center; gap: 8px; padding: 12px 16px; border-top: 1px solid #e0e0e0; background: #fff; position: sticky; bottom: 0; }
.upload-btn { flex-shrink: 0; }
.pending-preview { position: relative; flex-shrink: 0; }
.pending-preview img { height: 40px; border-radius: 6px; border: 1px solid #e0e0e0; }
.pending-remove { position: absolute; top: -6px; right: -6px; width: 18px; height: 18px; background: #ef4444; color: #fff; border-radius: 50%; font-size: 11px; display: flex; align-items: center; justify-content: center; cursor: pointer; }

.msg-image { margin-bottom: 8px; cursor: pointer; }
.msg-image img { max-width: 240px; max-height: 200px; border-radius: 10px; border: 1px solid #e0e0e0; display: block; }
.image-overlay { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,.85); z-index: 9999; display: flex; align-items: center; justify-content: center; cursor: zoom-out; }
.preview-full { max-width: 90vw; max-height: 90vh; border-radius: 8px; object-fit: contain; }
.preview-close { position: fixed; top: 20px; right: 24px; color: #fff; font-size: 28px; cursor: pointer; width: 44px; height: 44px; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: rgba(255,255,255,.15); }

.md-content { line-height: 1.8; font-size: 14px; }
.md-content h3 { font-size: 16px; margin: 16px 0 8px; color: #1a1a1a; }
.md-content h4 { font-size: 14px; margin: 12px 0 6px; color: #333; }
.md-content strong { color: #1a1a1a; }
.md-content code { background: #f0f0f0; padding: 1px 5px; border-radius: 3px; font-size: 13px; color: #e11d48; }
.md-content ul { margin: 4px 0; padding-left: 20px; }
.md-content li { margin: 2px 0; }
.md-content em { color: #666; }

.rc-card { background: #fff; border: 1px solid #e0e0e0; border-radius: 12px; padding: 14px; margin: 4px 0; }
.rc-card-title { font-size: 16px; margin: 0 0 4px; color: #1a1a1a; }
.rc-tags { font-size: 12px; color: #666; margin-bottom: 10px; }
.rc-sec { font-weight: 600; font-size: 13px; margin: 10px 0 4px; color: #444; }
.rc-item { font-size: 13px; color: #333; margin: 5px 0; padding-left: 2px; line-height: 1.6; }
.rc-note { font-size: 12px; color: #888; display: block; }
.rc-tip { background: #fefce8; border-radius: 8px; padding: 10px; font-size: 12px; color: #a16207; margin-top: 10px; line-height: 1.6; }

.rc-followups { margin-top: 12px; padding-top: 10px; border-top: 1px dashed #e0e0e0; display: flex; flex-wrap: wrap; gap: 6px; }
.rc-followup-title { font-size: 12px; color: #888; margin-bottom: 6px; width: 100%; }
.rc-followup-chip { display: inline-block; background: #f0fdf4; color: #16a34a; font-size: 12px; padding: 5px 12px; border-radius: 14px; cursor: pointer; border: 1px solid #bbf7d0; transition: .1s; white-space: nowrap; }
.rc-followup-chip:hover { background: #dcfce7; transform: scale(1.02); }
</style>
