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
        <div v-if="isWritingScene" class="writing-modes">
          <div class="wm-title">📝 选择写作类型快速开始</div>
          <div class="wm-grid">
            <div v-for="wm in writingModes" :key="wm.key" class="wm-chip" @click="startWriting(wm)">
              <span class="wm-icon">{{ wm.icon }}</span>
              <span class="wm-label">{{ wm.label }}</span>
            </div>
          </div>
        </div>
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
            <div v-html="msg._displayHtml || renderContent(msg.content)"></div>
          </div>
        </div>
        <div v-if="msg.role === 'assistant' && !msg._typing" class="msg-actions">
          <el-button text size="small" @click="copyMsg(i)">📋 复制</el-button>
          <el-button text size="small" @click="shareMsg(i)">📤 分享</el-button>
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

    <!-- 收藏分类弹窗 -->
    <el-dialog v-model="favDialog.show" title="选择分类" width="320px" :close-on-click-modal="false">
      <div class="fav-cat-grid">
        <div v-for="c in favCategories" :key="c.key" class="fav-cat-opt"
             :class="{ active: favDialog.selected === c.key }"
             @click="favDialog.selected = c.key">
          <span class="fco-icon">{{ c.icon }}</span>
          <span class="fco-label">{{ c.label }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="favDialog.show = false">取消</el-button>
        <el-button type="primary" @click="confirmFavorite">确定收藏</el-button>
      </template>
    </el-dialog>

    <!-- 图片预览 -->
    <div v-if="previewImg" class="image-overlay" @click="previewImg = null">
      <img :src="imgUrl(previewImg)" class="preview-full" />
      <div class="preview-close" @click="previewImg = null">✕</div>
    </div>

    <!-- 分享卡片弹窗 -->
    <el-dialog v-model="shareDialog.show" title="分享卡片" width="360px" :close-on-click-modal="true" top="5vh">
      <div class="share-card-preview" ref="shareCardRef">
        <div class="sc-header">
          <span class="sc-logo">🌿</span>
          <span class="sc-brand">LifeWise · AI 生活助手</span>
        </div>
        <div class="sc-body">
          <div class="sc-question" v-if="shareDialog.question">💬 {{ shareDialog.question }}</div>
          <div class="sc-divider"></div>
          <div class="sc-answer" v-html="shareDialog.html"></div>
        </div>
        <div class="sc-footer">
          <span class="sc-date">{{ shareDialog.date }}</span>
          <span class="sc-watermark">via LifeWise</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="shareDialog.show = false">取消</el-button>
        <el-button type="success" @click="downloadShareCard" :loading="shareDialog.downloading">
          📋 复制图片
        </el-button>
        <el-button type="primary" @click="copyShareText">
          📋 复制文本
        </el-button>
      </template>
    </el-dialog>

        <!-- 图片快捷操作栏 -->
    <div v-if="pendingImage" class="image-preview-bar">
      <div class="ipb-preview">
        <img :src="pendingImage" />
        <span class="ipb-remove" @click="pendingImage = null; pendingFile = null">✕</span>
      </div>
      <div class="ipb-actions">
        <span class="ipb-chip" @click="inputText = '请分析这张图片'; tempScene = 'cooking'; send()">🔍 分析图片</span>
        <span class="ipb-chip" @click="inputText = '请识别这张图片的内容'; tempScene = 'other'; send()">👀 识别内容</span>
        <span class="ipb-chip" @click="inputText = '请描述这张图片'; tempScene = 'writing'; send()">📝 描述图片</span>
      </div>
    </div>
    <div class="input-area">
      <el-button :icon="Microphone" circle size="small" @click="startVoice" :type="isListening ? 'danger' : 'default'" :disabled="loading" />
      <el-button class="upload-btn" :icon="Picture" circle size="small" @click="triggerUpload" :disabled="loading" />
      <input ref="fileInput" type="file" accept="image/*" style="display:none" @change="handleFileSelect" />
      <el-input v-model="inputText" ref="inputRef" placeholder="输入你的问题..." size="large"
                @keyup.enter="send" :disabled="loading" clearable />
      <el-button type="success" :icon="Promotion" circle @click="send" :disabled="loading || !inputText.trim() && !pendingFile" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getConversation, sendChat, addFavorite, removeFavorite, uploadImage, updateFavoriteCategory } from '../api'
import { ArrowLeft, DocumentCopy, Microphone, Picture, Promotion, Collection, Share } from '@element-plus/icons-vue'
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
const tempScene = ref('')
const shareCardRef = ref(null)
const isListening = ref(false)
const favDialog = ref({ show: false, selected: 'other', msgIndex: -1 })
const shareDialog = ref({ show: false, question: '', html: '', date: '', downloading: false })
const favCategories = [
  { key: 'cooking', icon: '🍳', label: '做饭' },
  { key: 'shopping', icon: '🛒', label: '买菜' },
  { key: 'repair', icon: '🔧', label: '修理' },
  { key: 'housework', icon: '🏠', label: '家务' },
  { key: 'health', icon: '🌞', label: '健康' },
  { key: 'fashion', icon: '👔', label: '穿搭' },
  { key: 'etiquette', icon: '🎂', label: '礼仪' },
  { key: 'pet', icon: '🐥', label: '宠物' },
  { key: 'mealplan', icon: '📮', label: '食谱' },
  { key: 'writing', icon: '✍️', label: '写作' },
  { key: 'other', icon: '💬', label: '其他' }
]

const writingModes = [
  { key: 'diary', icon: '📔', label: '日记', prompt: '帮我写一篇今天的日记' },
  { key: 'todo', icon: '✅', label: '待办清单', prompt: '帮我整理一份待办清单' },
  { key: 'memo', icon: '📌', label: '备忘录', prompt: '帮我写一个备忘录' },
  { key: 'note', icon: '📝', label: '学习笔记', prompt: '帮我整理学习笔记' },
  { key: 'shopping', icon: '🛍️', label: '购物清单', prompt: '帮我列一个购物清单' },
  { key: 'idea', icon: '💡', label: '灵感记录', prompt: '帮我记录一个想法' }
]

const isWritingScene = computed(() => {
  const scene = localStorage.getItem('currentScene')
  return scene === 'writing'
})

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
  } catch(e) { console.error('[IMG] upload err:',e); ElMessage.error('加载对话失败') }
  finally { loading.value = false }
}

async function send() { console.log("[IMG] called, file=", !!pendingFile.value, "txt=", inputText.value)
  const msg = inputText.value.trim()
  if (!msg && !pendingFile.value) return

  const scene = tempScene.value || localStorage.getItem('currentScene') || 'other'; tempScene.value = ''
  const convId = route.params.id ? Number(route.params.id) : null
  let imageUrl = ''

  if (pendingFile.value) {
    try {
      const uploadRes = await uploadImage(pendingFile.value)
      imageUrl = uploadRes.data?.url || ""; console.log("[IMG] upload url:", imageUrl); ''
      pendingFile.value = null; pendingImage.value = null
    } catch(e) { console.error('[IMG] upload err:',e); ElMessage.error('图片上传失败'); return }
  }

  messages.value.push({ _id: 'user-' + Date.now(), role: 'user', content: msg, imageUrl, _typing: false, _displayHtml: '', _faved: false })
  inputText.value = ''
  await nextTick(); scrollBottom()

  loading.value = true; currentTyping.value = true
  const aiIdx = messages.value.length
  messages.value.push({ _id: 'ai-' + Date.now(), role: 'assistant', content: '', imageUrl: '', _typing: true, _displayHtml: '', _faved: false })

  try {
    const res = await sendChat(msg, scene, convId, imageUrl)
    const m = messages.value[aiIdx]
    if (res.data?.id && m) { m._id = res.data.id }
    const _ct = res.data?.content||""; const _ctStr = typeof _ct === "string" ? _ct : JSON.stringify(_ct); console.log("[IMG] chat res keys:", Object.keys(res||{}), "data_keys:", Object.keys(res.data||{}), "content_len:", _ctStr.length, "typeof:", typeof _ct, "first:", _ctStr.charCodeAt(0), _ctStr.charCodeAt(1), "json_parse_ok:", (()=>{try{JSON.parse(_ctStr);return true}catch(e){console.warn("[JSON] parse error:",e.message);return false}})()); const fullContent = typeof res.data === "string" ? res.data : (res.data?.content || res.data?.answer || JSON.stringify(res.data))
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

async function shareMsg(i) {
  const msg = messages.value[i]; if (!msg) return
  const prevMsg = i > 0 ? messages.value[i-1] : null
  const question = prevMsg?.role === 'user' ? prevMsg.content : ''
  
  // Get scene label for context
  const sceneLabel = localStorage.getItem('sceneLabel') || '生活常识'
  
  // Prepare HTML for card (strip tags for plain text view)
  let html = msg._displayHtml || renderContent(msg.content)
  
  // Clean up the HTML - remove action buttons, follow-ups etc
  const tempDiv = document.createElement('div')
  tempDiv.innerHTML = html
  // Remove follow-up sections
  tempDiv.querySelectorAll('.rc-followups')?.forEach(el => el.remove())
  html = tempDiv.innerHTML
  
  shareDialog.value = {
    show: true,
    question: question,
    html: html,
    date: new Date().toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }),
    downloading: false
  }
}

async function downloadShareCard() {
  const html2canvas = (await import('html2canvas')).default
  shareDialog.value.downloading = true
  await nextTick()
  try {
    const el = shareCardRef.value
    if (!el) return
    const canvas = await html2canvas(el, {
      scale: 2,
      backgroundColor: '#ffffff',
      useCORS: true,
      logging: false,
      width: el.scrollWidth,
      height: el.scrollHeight
    })
    // 尝试复制图片到剪贴板
    if (typeof ClipboardItem !== 'undefined') {
      try {
        const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/png'))
        await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })])
        ElMessage.success('图片已复制到剪贴板')
        shareDialog.value.downloading = false
        return
      } catch {}
    }
    // 降级1：下载图片
    try {
      const link = document.createElement('a')
      link.download = 'lifewise-card-' + Date.now() + '.png'
      link.href = canvas.toDataURL('image/png')
      link.click()
      ElMessage.success('图片已下载')
      shareDialog.value.downloading = false
      return
    } catch {}
    // 降级2：新标签打开（内置浏览器最稳妥）
    const dataUrl = canvas.toDataURL('image/png')
    window.open(dataUrl, '_blank')
    ElMessage.success('图片已在新标签打开，可右键保存')
  } catch (e) {
    ElMessage.error('生成分享图片失败')
    console.error(e)
  } finally {
    shareDialog.value.downloading = false
  }
}

function copyShareText() {
  const d = shareDialog.value
  let text = '🌿 LifeWise · AI 生活助手\n'
  if (d.question) text += '💬 ' + d.question + '\n'
  text += '━━━━━━━━━━━━━━\n'
  // Strip HTML tags for plain text
  const temp = document.createElement('div')
  temp.innerHTML = d.html
  text += temp.textContent || temp.innerText || ''
  text += '\n━━━━━━━━━━━━━━\n'
  text += 'via LifeWise - ' + d.date
  
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('文本已复制，可以粘贴分享')
  }).catch(() => {
    const ta = document.createElement('textarea'); ta.value = text
    document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta)
    ElMessage.success('文本已复制')
  })
}
async function toggleFavorite(i) {
  const msg = messages.value[i]; if (!msg) return
  if (msg._faved) {
    try { await removeFavorite(msg._id); msg._faved = false; ElMessage.success('已取消收藏') }
    catch { ElMessage.error('操作失败') }
  } else {
    favDialog.value.selected = 'other'
    favDialog.value.msgIndex = i
    favDialog.value.show = true
  }
}

async function confirmFavorite() {
  const i = favDialog.value.msgIndex
  const msg = messages.value[i]; if (!msg) return
  favDialog.value.show = false
  try {
    await addFavorite(msg._id, '', favDialog.value.selected)
    msg._faved = true
    ElMessage.success('已收藏')
  } catch(e) { console.error('[IMG] upload err:',e); ElMessage.error('收藏失败') }
}

function startWriting(wm) {
  inputText.value = wm.prompt
  send()
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
  if (!('webkitSpeechRecognition' in window) && !('SpeechRecognition' in window)) {
    ElMessage.warning('当前浏览器不支持语音识别，建议用 Chrome 浏览器打开 http://localhost:5173')
    return
  }
  // Check if permission already denied
  if (navigator.permissions) {
    navigator.permissions.query({ name: 'microphone' }).then(result => {
      if (result.state === 'denied') {
        ElMessage.error('麦克风权限已被禁用，请在浏览器地址栏左侧点击🔒开启麦克风权限')
        return
      }
    }).catch(() => {})
  }
  // Use webkitSpeechRecognition explicitly and keep a global reference to prevent GC
  window._voiceRecognition = new (window.webkitSpeechRecognition || window.SpeechRecognition)()
  const r = window._voiceRecognition
  r.lang = 'zh-CN'; r.continuous = true; r.interimResults = true
  isListening.value = true
  ElMessage.info('🎤 说话吧，说完停顿几秒即自动发送...')
  r.onresult = (e) => {
    let t = ''
    for (let i = e.resultIndex; i < e.results.length; i++) {
      t += e.results[i][0].transcript
    }
    inputText.value = t
  }
  r.onerror = (e) => {
    isListening.value = false; window._voiceRecognition = null
    if (e.error === 'not-allowed') ElMessage.error('麦克风被拒绝，网址栏左侧允许后重试')
    else if (e.error === 'no-speech') ElMessage.warning('没听到说话，重试吧')
    else if (e.error === 'aborted') ElMessage.warning('麦克风被中断，点击微音符重试')
    else ElMessage.error('识别失败: ' + e.error)
  }
  r.onend = () => {
    isListening.value = false; window._voiceRecognition = null
    if (inputText.value.trim()) setTimeout(() => send(), 300)
  }
  r.start()
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
  let html = (text || "");
  html = html.replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;")
    .replace(/^#### (.+)$/gm, "<h5>$1</h5>")
    .replace(/^### (.+)$/gm, "<h4>$1</h4>")
    .replace(/^## (.+)$/gm, "<h3>$1</h3>")
    .replace(/^# (.+)$/gm, "<h2>$1</h2>")
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/\*(.+?)\*/g, "<em>$1</em>")
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/^> (.+)$/gm, "<blockquote>$1</blockquote>")
    .replace(/^- (.+)$/gm, "<li>$1</li>")
    .replace(/^\* (.+)$/gm, "<li>$1</li>")
    .replace(/^\d+\.\s+(.+)$/gm, "<li>$1</li>")
    .replace(/\n/g, "<br>")
    .replace(/((?:<li>.*<\/li>\n?)+)/g, '<ul class="md-list">$1</ul>')
    .replace(/<\/li><br>/g, "</li>")
    .replace(/<h([2345])><br>/g, "<h$1>")
    .replace(/<br><\/h([2345])>/g, "</h$1>")
    .replace(/<br><blockquote>/g, "<blockquote>")
    .replace(/<\/blockquote><br>/g, "</blockquote>")
    .replace(/<br><ul class="md-list">/g, '<ul class="md-list">')
    .replace(/<\/ul><br>/g, "</ul>")
  return '<div class="md-content writing-content">' + html + "</div>"
}

function tryParseJsonSafe(text) {
  if (!text) return null
  // Helper: try parse with trailing comma fix
  function tryParse(s) {
    try { const obj = JSON.parse(s); if (obj && typeof obj === 'object') return obj } catch {}
    try { const fixed = s.replace(/,([\s\n\r]*[}\]])/g, '$1'); const obj = JSON.parse(fixed); if (obj && typeof obj === 'object') return obj } catch {}
    return null
  }
  // Step 1: direct parse (trim first)
  let r = tryParse(text.trim()); if (r) return r
  // Step 2: remove code fences
  let clean = text.replace(/```(?:json)?\s*/gi, '').replace(/```/g, '').trim()
  r = tryParse(clean); if (r) return r
  // Step 3: regex extract JSON
  const m = clean.match(/\{[\s\S]*\}/)
  if (m) {
    r = tryParse(m[0]); if (r) return r
    r = tryParse(m[0].replace(/[\x00-\x1f\x7f-\x9f]/g, '')); if (r) return r
  }
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
      const stepImg = s.step_image
        ? (() => {
            const emoji = stepEmoji(s.step_image)
            const cls = stepGradient(s.step_image)
            return `<div class="rc-step-img"><img class="rc-step-photo" src="http://localhost:8080/api/images/step-img?q=${encodeURIComponent(s.step_image)}" alt="${esc(s.step_image)}" loading="lazy" onerror="this.style.display='none';this.nextElementSibling.style.display='flex'"/><div class="rc-step-illustration ${cls}" style="display:none"><span>${emoji}</span></div></div>`
          })()
        : ''
      const tip = s.tip ? `<span class="rc-note">💡 ${esc(s.tip)}</span>` : ''
      const warning = s.warning ? `<span class="rc-warning">⚠️ ${esc(s.warning)}</span>` : ''
      parts.push(`<div class="rc-step"><div class="rc-step-badge">${s.step || ''}</div><div class="rc-step-body">${stepImg}<div class="rc-step-text">${esc(s.action || s)}${tip}${warning}</div></div></div>`)
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
    const ta = Array.isArray(data.tips) ? data.tips : [data.tips]
    ta.forEach(t => parts.push(`<div class="rc-item">· ${esc(t)}</div>`))
  }
  if (data.key_point) parts.push(`<div class="rc-tip">🔥 ${esc(data.key_point)}</div>`)
  if (data.safety_tip) parts.push(`<div class="rc-tip" style="color:#dc2626">⚠️ ${esc(data.safety_tip)}</div>`)
  if (data.answer) parts.push(`<div class="rc-item" style="margin-top:8px">${esc(data.answer).replace(/\n/g, '<br>')}</div>`)
  if (data.suggestions) {
    parts.push('<div class="rc-sec">💡 建议</div>')
    const sa = Array.isArray(data.suggestions) ? data.suggestions : [data.suggestions]
    sa.forEach(s => {
      if (typeof s === 'string') parts.push(`<div class="rc-item">· ${esc(s)}</div>`)
      else parts.push(`<div class="rc-item"><strong>${esc(s.item)}</strong>：${esc(s.detail)}</div>`)
    })
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

function stepEmoji(keyword) {
  if (!keyword) return '🍳'
  const kw = keyword.toLowerCase()
  if (/cut|chop|dice|slice|mince/.test(kw)) return '🔪'
  if (/wash|rinse|clean|peel/.test(kw)) return '🚿'
  if (/fry|stir.?fry|saute|pan/.test(kw)) return '🍳'
  if (/boil|cook|simmer|stew|braise|blanch/.test(kw)) return '🥘'
  if (/steam/.test(kw)) return '♨️'
  if (/bake|roast|oven/.test(kw)) return '🔥'
  if (/season|marinate|salt|sugar|sauce|soy/.test(kw)) return '🧂'
  if (/mix|stir|whisk|beat|blend/.test(kw)) return '🥄'
  if (/egg/.test(kw)) return '🥚'
  if (/meat|ribs|chicken|pork|beef|fish/.test(kw)) return '🥩'
  if (/vegetable|tomato|onion|garlic|ginger/.test(kw)) return '🥬'
  if (/oil|heat/.test(kw)) return '🔥'
  if (/serve|plate|dish|bowl/.test(kw)) return '🍽️'
  if (/garnish|green.?onion|herb/.test(kw)) return '🌿'
  if (/pour|add|drizzle/.test(kw)) return '🫗'
  return '🍳'
}

function stepGradient(keyword) {
  if (!keyword) return 'grad-cook'
  const kw = keyword.toLowerCase()
  if (/cut|chop|dice|slice|mince/.test(kw)) return 'grad-cut'
  if (/wash|rinse|clean|peel/.test(kw)) return 'grad-wash'
  if (/fry|stir.?fry|saute|pan/.test(kw)) return 'grad-fry'
  if (/boil|cook|simmer|stew|braise|blanch/.test(kw)) return 'grad-boil'
  if (/steam/.test(kw)) return 'grad-steam'
  if (/bake|roast|oven/.test(kw)) return 'grad-bake'
  if (/season|marinate|salt|sugar|sauce|soy/.test(kw)) return 'grad-season'
  if (/mix|stir|whisk|beat|blend/.test(kw)) return 'grad-mix'
  if (/egg/.test(kw)) return 'grad-egg'
  if (/meat|ribs|chicken|pork|beef|fish/.test(kw)) return 'grad-meat'
  if (/vegetable|tomato|onion|garlic|ginger/.test(kw)) return 'grad-veg'
  if (/oil|heat/.test(kw)) return 'grad-oil'
  if (/serve|plate|dish|bowl/.test(kw)) return 'grad-serve'
  if (/garnish|green.?onion|herb/.test(kw)) return 'grad-garnish'
  if (/pour|add|drizzle/.test(kw)) return 'grad-pour'
  return 'grad-cook'
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
.image-preview-bar { display: flex; align-items: center; gap: 12px; padding: 10px 16px; background: #fff; border-top: 1px solid #e5e7eb; border-bottom: 1px solid #e5e7eb; }
.ipb-preview { position: relative; flex-shrink: 0; }
.ipb-preview img { height: 40px; width: 40px; border-radius: 6px; border: 1px solid #e0e0e0; object-fit: cover; }
.ipb-remove { position: absolute; top: -6px; right: -6px; width: 18px; height: 18px; background: #ef4444; color: #fff; border-radius: 50%; font-size: 11px; display: flex; align-items: center; justify-content: center; cursor: pointer; }
.msg-image { margin-bottom: 8px; cursor: pointer; }
.msg-image img { max-width: 240px; max-height: 200px; border-radius: 10px; border: 1px solid #e0e0e0; display: block; }
.image-overlay { position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,.85); z-index: 9999; display: flex; align-items: center; justify-content: center; cursor: zoom-out; }
.preview-full { max-width: 90vw; max-height: 90vh; border-radius: 8px; object-fit: contain; }
.preview-close { position: fixed; top: 20px; right: 24px; color: #fff; font-size: 28px; cursor: pointer; width: 44px; height: 44px; display: flex; align-items: center; justify-content: center; border-radius: 50%; background: rgba(255,255,255,.15); }
.md-content { line-height: 1.8; font-size: 14px; color: #333; }
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

/* 收藏分类弹窗 */
.fav-cat-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.fav-cat-opt { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 12px 8px; border-radius: 12px; cursor: pointer; background: #f9fafb; border: 2px solid transparent; transition: .15s; }
.fav-cat-opt:hover { background: #f0fdf4; }
.fav-cat-opt.active { background: #f0fdf4; border-color: #22c55e; }
.fav-cat-opt .fco-icon { font-size: 24px; }
.fav-cat-opt .fco-label { font-size: 12px; color: #555; }

/* ??????? */
.writing-modes { margin-top: 20px; text-align: left; padding: 0 20px; }
.wm-title { font-size: 13px; color: #888; margin-bottom: 10px; }
.wm-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.wm-chip { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 12px 6px; border-radius: 12px; cursor: pointer; background: #f9fafb; border: 1px solid #eee; transition: .15s; }
.wm-chip:hover { background: #f0fdf4; border-color: #bbf7d0; transform: translateY(-1px); }
.wm-chip:active { transform: scale(.96); }
.wm-icon { font-size: 24px; }
.wm-label { font-size: 12px; color: #555; font-weight: 500; }

/* ?????? */
.writing-content { line-height: 1.9; font-size: 14.5px; color: #2d2d2d; }
.writing-content h2 { font-size: 18px; margin: 20px 0 10px; color: #111; padding-bottom: 6px; border-bottom: 2px solid #22c55e; }
.writing-content h3 { font-size: 16px; margin: 16px 0 8px; color: #1a1a1a; }
.writing-content h4 { font-size: 15px; margin: 14px 0 6px; color: #222; }
.writing-content h5 { font-size: 14px; margin: 12px 0 6px; color: #333; }
.writing-content strong { color: #111; }
.writing-content code { background: #f0f0f0; padding: 1px 6px; border-radius: 4px; font-size: 13px; color: #e11d48; }
.writing-content blockquote { border-left: 3px solid #22c55e; padding: 6px 14px; margin: 8px 0; color: #555; background: #fafcfa; border-radius: 0 6px 6px 0; font-style: italic; }
.writing-content .md-list { padding-left: 20px; margin: 4px 0; }
.writing-content .md-list li { margin: 3px 0; }
/* 分享卡片 */
.share-card-preview { background: #fff; border-radius: 16px; overflow: hidden; font-family: -apple-system, BlinkMacSystemFont, sans-serif; max-height: 70vh; overflow-y: auto; }
.sc-header { display: flex; align-items: center; gap: 8px; padding: 16px 20px; background: linear-gradient(135deg, #f0fdf4, #dcfce7); border-bottom: 1px solid #bbf7d0; }
.sc-logo { font-size: 24px; }
.sc-brand { font-size: 14px; font-weight: 600; color: #16a34a; }
.sc-body { padding: 16px 20px; }
.sc-question { font-size: 15px; font-weight: 600; color: #1a1a1a; margin-bottom: 8px; padding: 10px 14px; background: #f0fdf4; border-radius: 10px; border-left: 3px solid #22c55e; }
.sc-divider { height: 1px; background: #e5e7eb; margin: 12px 0; }
.sc-answer { font-size: 14px; color: #333; line-height: 1.7; }
.sc-answer .rc-card { border: none; padding: 0; margin: 0; box-shadow: none; }
.sc-answer .rc-card-title { font-size: 18px; margin-bottom: 8px; }
.sc-answer .rc-followups { display: none; }
.sc-answer .rc-tip { background: #fefce8; padding: 8px 12px; border-radius: 8px; font-size: 13px; }
.sc-answer .rc-sec { font-size: 14px; font-weight: 600; margin: 12px 0 6px; color: #16a34a; }
.sc-footer { display: flex; justify-content: space-between; align-items: center; padding: 12px 20px; border-top: 1px solid #e5e7eb; font-size: 11px; color: #aaa; }
.sc-watermark { color: #22c55e; font-weight: 500; }

.ipb-actions { display: flex; gap: 6px; flex-wrap: nowrap; }
.ipb-chip { display: inline-flex; align-items: center; gap: 3px; background: #fff; color: #374151; font-size: 12px; padding: 4px 10px; border-radius: 6px; cursor: pointer; border: 1px solid #d1d5db; transition: .15s; white-space: nowrap; font-weight: 400; }
.ipb-chip:hover { background: #f0fdf4; border-color: #22c55e; color: #16a34a; }
</style>
<style>

/* Unscoped styles for v-html content (scoped CSS does not apply to v-html) */
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
.md-content { line-height: 1.8; font-size: 14px; color: #333; }
.md-content h3 { font-size: 16px; margin: 16px 0 8px; color: #1a1a1a; }
.md-content h4 { font-size: 14px; margin: 12px 0 6px; color: #333; }
.md-content strong { color: #1a1a1a; }
.md-content code { background: #f0f0f0; padding: 1px 5px; border-radius: 3px; font-size: 13px; color: #e11d48; }
.md-content ul { margin: 4px 0; padding-left: 20px; }
.md-content li { margin: 2px 0; }
.md-content em { color: #666; }
/* 步骤配图样式 */
.rc-step { display: flex; gap: 10px; margin: 10px 0; padding: 0; align-items: flex-start; }
.rc-step-badge { flex-shrink: 0; width: 28px; height: 28px; background: linear-gradient(135deg, #22c55e, #16a34a); color: #fff; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 700; margin-top: 2px; box-shadow: 0 2px 6px rgba(34,197,94,.25); }
.rc-step-body { flex: 1; min-width: 0; }
.rc-step-text { font-size: 13px; color: #333; line-height: 1.6; padding: 2px 0; }
.rc-step-img { margin: 6px 0 2px; border-radius: 12px; overflow: hidden; position: relative; }
.rc-step-photo { width: 100%; height: 180px; object-fit: cover; display: block; border-radius: 12px; }
.rc-step-illustration { width: 100%; height: 160px; display: flex; align-items: center; justify-content: center; border-radius: 12px; font-size: 72px; position: relative; }
.rc-step-illustration span { filter: drop-shadow(0 2px 4px rgba(0,0,0,.1)); z-index: 1; }
/* 步骤插画渐变色背景 */
.rc-step-illustration.grad-cut { background: linear-gradient(135deg, #fef3c7, #fde68a); }
.rc-step-illustration.grad-wash { background: linear-gradient(135deg, #e0f2fe, #bae6fd); }
.rc-step-illustration.grad-fry { background: linear-gradient(135deg, #fce4ec, #f8bbd0); }
.rc-step-illustration.grad-boil { background: linear-gradient(135deg, #fff3e0, #ffe0b2); }
.rc-step-illustration.grad-steam { background: linear-gradient(135deg, #f3e8ff, #e9d5ff); }
.rc-step-illustration.grad-bake { background: linear-gradient(135deg, #fef3c7, #fdba74); }
.rc-step-illustration.grad-season { background: linear-gradient(135deg, #f0fdf4, #bbf7d0); }
.rc-step-illustration.grad-mix { background: linear-gradient(135deg, #fdf2f8, #fbcfe8); }
.rc-step-illustration.grad-egg { background: linear-gradient(135deg, #fef9c3, #fde047); }
.rc-step-illustration.grad-meat { background: linear-gradient(135deg, #fee2e2, #fecaca); }
.rc-step-illustration.grad-veg { background: linear-gradient(135deg, #dcfce7, #86efac); }
.rc-step-illustration.grad-oil { background: linear-gradient(135deg, #fff7ed, #fed7aa); }
.rc-step-illustration.grad-serve { background: linear-gradient(135deg, #f0fdf4, #a7f3d0); }
.rc-step-illustration.grad-garnish { background: linear-gradient(135deg, #ecfdf5, #6ee7b7); }
.rc-step-illustration.grad-pour { background: linear-gradient(135deg, #eff6ff, #93c5fd); }
.rc-step-illustration.grad-cook { background: linear-gradient(135deg, #ecfdf5, #a7f3d0); }
.rc-step .rc-note { font-size: 12px; color: #888; margin-top: 3px; }
.rc-step .rc-warning { font-size: 12px; color: #dc2626; display: block; margin-top: 2px; }
</style>


