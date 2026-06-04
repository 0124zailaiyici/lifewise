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

      <div v-for="(msg, i) in messages" :key="i" :class="'msg msg-' + msg.role" :data-msg-id="msg._id || ''" :data-msg-index="i">
        <div class="bubble">
          <div v-if="msg._typing"><span v-html="msg._displayHtml"></span><span class="cursor">|</span></div>
          <div v-else>
            <!-- 用户图片 -->
            <div v-if="msg.imageUrl" class="msg-image" @click="previewImage(msg.imageUrl)">
              <img :src="imgUrl(msg.imageUrl)" alt="图片" loading="lazy" />
            </div>
            <!-- 内容 -->
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

    <!-- 图片预览弹窗 -->
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
import { ref, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { sendChat, getConversation, addFavorite, removeFavorite, uploadImage, renameConversation } from '../api'
import { Promotion, Picture, Microphone } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const msgBox = ref(null)
const inputRef = ref(null)
const fileInput = ref(null)
const messages = ref([])
const inputText = ref('')
const loading = ref(false)
const convId = ref(null)
const currentScene = ref(localStorage.getItem('currentScene') || '')
const currentLabel = ref(localStorage.getItem('sceneLabel') || 'AI 助手')
const currentTyping = ref(false)
const highlightMsgId = ref(route.query.highlight || null)

// 图片相关
const pendingFile = ref(null)
const pendingImage = ref(null)  // base64 preview
const previewImg = ref(null)
const isListening = ref(false)
const recognition = ref(null)

function imgUrl(url) {
  if (!url) return ''
  if (url.startsWith('http')) return url
  return 'http://localhost:8080' + url
}

function triggerUpload() { fileInput.value?.click() }

async function handleFileSelect(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) { ElMessage.warning('请选择图片文件'); return }
  if (file.size > 10 * 1024 * 1024) { ElMessage.warning('图片不能超过10MB'); return }
  pendingFile.value = file
  // 本地预览
  const reader = new FileReader()
  reader.onload = (ev) => { pendingImage.value = ev.target?.result }
  reader.readAsDataURL(file)
  e.target.value = ''
}

function handleFollowUpClick(e) {
  if (!msgBox.value) return
  const chip = e.target.closest('.rc-followup-chip')
  if (chip) {
    inputText.value = chip.textContent
    send()
  }
}

onMounted(async () => {
  msgBox.value?.addEventListener('click', handleFollowUpClick)
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
      // 收藏高亮跳转
      if (highlightMsgId.value) {
        await nextTick()
        setTimeout(() => {
          const el = msgBox.value?.querySelector('[data-msg-id="' + highlightMsgId.value + '"]')
          if (el) {
            el.scrollIntoView({ behavior: 'smooth', block: 'center' })
            el.classList.add('msg-highlight')
            setTimeout(() => el.classList.remove('msg-highlight'), 2500)
          }
        }, 300)
      }
    } catch (e) {
      ElMessage.error('加载对话失败')
    }
  }
})

async function handleRenameTitle() {
  if (!convId.value) return
  const { value: newTitle } = await ElMessageBox.prompt('输入新标题', '重命名对话', {
    inputValue: currentLabel.value,
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValidator: v => v?.trim() ? true : '标题不能为空'
  })
  if (newTitle?.trim()) {
    try {
      await renameConversation(convId.value, newTitle.trim())
      currentLabel.value = newTitle.trim()
      ElMessage.success('已重命名')
    } catch (e) {
      ElMessage.error('重命名失败')
    }
  }
}

async function send() {
  const text = inputText.value.trim()
  if ((!text && !pendingFile.value) || loading.value) return
  if (currentTyping.value) return

  loading.value = true
  const _sendTime = Date.now() // 记录发送时间，用于判断是否为缓存命中

  // 先上传图片
  let imageUrl = ''
  if (pendingFile.value) {
    try {
      const uploadRes = await uploadImage(pendingFile.value)
      imageUrl = uploadRes.data.url
      pendingFile.value = null
      pendingImage.value = null
    } catch (e) {
      ElMessage.error('图片上传失败')
      loading.value = false
      return
    }
  }

  // 图片单独作为一条消息
  if (imageUrl) {
    messages.value.push({ role: 'user', content: '[图片]', imageUrl })
  }
  // 文字单独作为一条消息
  if (text) {
    messages.value.push({ role: 'user', content: text })
  }
  // 如果没有文字也没有图片就不发
  if (!imageUrl && !text) { loading.value = false; return }
  inputText.value = ''
  scrollBottom()

  try {
    const res = await sendChat(text || '请分析这张图片', currentScene.value, convId.value, imageUrl || undefined)
    const reply = res.data
    const fullContent = reply.content

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

    const msgIdx = messages.value.length - 1
    const fullHtml = renderContent(fullContent)
    const elapsed = Date.now() - _sendTime

    // 快速响应（<800ms）说明缓存命中，直接显示不用打字动画
    if (elapsed < 800) {
      const m = messages.value[msgIdx]
      if (m) {
        m._typing = false
        m._displayHtml = fullHtml
        currentTyping.value = false
        scrollBottom()
      }
    } else {
      let charIdx = 0
      const speed = 50
      const batchSize = 5

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
        if (charIdx % 15 < batchSize) scrollBottom()
      }, speed)
    }

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
    const obj = tryParseJson(text)
    if (obj && typeof obj === 'object') text = formatObjForCopy(obj)
  } catch (e) {}
  doCopy(text)
}

function tryParseJson(text) {
  let clean = text.trim()
  if (clean.startsWith('```')) {
    clean = clean.replace(/^```(?:json)?\s*\n?/, '').replace(/\n?```\s*$/, '')
  }
  return JSON.parse(clean)
}

function formatObjForCopy(obj) {
  let parts = []
  if (obj.title) parts.push('【' + obj.title + '】')
  if (obj.ingredients) {
    parts.push(''); parts.push('食材：')
    obj.ingredients.forEach(i => {
      if (typeof i === 'object') parts.push('  ' + (i.name || '') + (i.amount ? ' ' + i.amount : '') + (i.note ? '（' + i.note + '）' : ''))
      else parts.push('  ' + i)
    })
  }
  if (obj.materials) {
    parts.push(''); parts.push('材料：')
    obj.materials.forEach(m => {
      if (typeof m === 'object') parts.push('  ' + (m.name || '') + (m.alternative ? '（可替代：' + m.alternative + '）' : ''))
      else parts.push('  ' + m)
    })
  }
  if (obj.steps) {
    parts.push(''); parts.push('步骤：')
    obj.steps.forEach(s => {
      if (typeof s === 'object') parts.push('  ' + (s.step ? s.step + '. ' : '') + (s.action || s.tip || ''))
      else parts.push('  ' + s)
    })
  }
  const selSteps = obj.selection_steps || obj.挑选步骤
  if (selSteps) {
    selSteps.forEach(s => {
      if (typeof s === 'object') {
        parts.push('')
        if (s.step_name || s.步骤名称) parts.push(s.step_name || s.步骤名称)
        if (s.action || s.具体操作) parts.push('  ' + (s.action || s.具体操作))
      }
    })
  }
  if (obj.tips) {
    parts.push('')
    const tips = Array.isArray(obj.tips) ? obj.tips : [obj.tips]
    tips.forEach(t => parts.push('提示：' + t))
  }
  if (obj.key_point) { parts.push(''); parts.push('关键：' + obj.key_point) }
  if (obj.summary_slogan) { parts.push(''); parts.push(obj.summary_slogan) }
  if (obj.common_mistakes) { parts.push(''); parts.push('误区：'); obj.common_mistakes.forEach(m => parts.push('  ' + m)) }
  if (obj.answer) parts.push(obj.answer)
  if (obj.safety_tip) parts.push('安全提示：' + obj.safety_tip)
  if (obj.storage_tip) parts.push('保存：' + obj.storage_tip)
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

function previewImage(url) { previewImg.value = url }

async function toggleFavorite(index) {
  const msg = messages.value[index]
  if (!msg || !msg._id) { ElMessage.warning('无法收藏该消息'); return }
  try {
    if (msg._faved) { await removeFavorite(msg._id); msg._faved = false; ElMessage.success('已取消收藏') }
    else { await addFavorite(msg._id, ''); msg._faved = true; ElMessage.success('已收藏') }
  } catch { ElMessage.error('操作失败') }
}


function startVoice() {
  try {
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition
    if (!SR) { ElMessage.warning('浏览器不支持语音输入'); return }
    if (isListening.value) {
      recognition.value?.stop(); isListening.value = false
      ElMessage.info('已停止聆听')
      return
    }
    const r = new SR()
    r.lang = 'zh-CN'; r.continuous = false; r.interimResults = true
    r.onresult = (e) => {
      const t = e.results[e.results.length-1][0].transcript
      inputText.value = t
    }
    r.onerror = (e) => {
      isListening.value = false
      if (e.error === 'not-allowed') ElMessage.warning('请允许麦克风权限')
      else ElMessage.warning('语音识别失败: ' + e.error)
    }
    r.onend = () => { isListening.value = false }
    r.start(); recognition.value = r; isListening.value = true
    ElMessage.success('请说话...')
  } catch (e) { ElMessage.warning('语音输入不可用: ' + e.message) }
}


function renderContent(content) {
  if (!content) return ''
  const data = tryParseJsonSafe(content)
  if (data) return renderStructured(data)
  // 非 JSON 内容：渲染为 Markdown
  return renderMarkdown(content)
}

// 基础 Markdown 渲染
function renderMarkdown(text) {
  let html = escapeHtml(text)
    // 代码块
    // 标题 (## 和 ###)
    .replace(/^### (.+)$/gm, '<h4>$1</h4>')
    .replace(/^## (.+)$/gm, '<h3>$1</h3>')
    // 粗体和斜体
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    // 行内代码
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    // 无序列表
    .replace(/^[-*] (.+)$/gm, '<li>$1</li>')
    // 有序列表
    .replace(/^\d+\.\s+(.+)$/gm, '<li>$1</li>')
    // 换行
    .replace(/\n/g, '<br>')
    // 合并连续的 li
    .replace(/(<li>.*?<\/li>)(<br>\s*)(?=<li>)/g, '$1\n')
    // 包裹列表
    .replace(/((?:<li>.*<\/li>\n?)+)/g, '<ul>$1</ul>')
  return '<div class="md-content">' + html + '</div>'
}

function tryParseJsonSafe(text) {
  try {
    const obj = tryParseJson(text)
    if (obj && typeof obj === 'object') return obj
  } catch (e) {}
  return null
}

function escapeHtml(s) {
  if (typeof s !== 'string') return ''
  return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
}

function renderStructured(data) {
  let parts = []

  if (data.title) {
    parts.push(`<h4>${escapeHtml(data.title)}</h4>`)
    let tags = ''
    if (data.difficulty) tags += `📊 ${escapeHtml(data.difficulty)}`
    if (data.time) tags += ` &middot; ⏱️ ${escapeHtml(data.time)}`
    if (data.servings) tags += ` &middot; ${escapeHtml(data.servings)}`
    if (tags) parts.push(`<div class="rc-tags">${tags}</div>`)
  } else if (data.品类) parts.push(`<h4>🛒 ${escapeHtml(data.品类)}</h4>`)
  else if (data.problem) {
    parts.push(`<h4>🔧 ${escapeHtml(data.problem)}</h4>`)
    if (data.severity) parts.push(`<div class="rc-tags">严重程度：${escapeHtml(data.severity)}</div>`)
  } else if (data.question) parts.push(`<h4>${escapeHtml(data.question)}</h4>`)

  if (Array.isArray(data.ingredients)) {
    parts.push('<div class="rc-sec">🥘 食材</div>')
    data.ingredients.forEach(i => renderItem(parts, i))
  }
  if (Array.isArray(data.materials)) {
    parts.push('<div class="rc-sec">🧴 材料</div>')
    data.materials.forEach(m => renderItem(parts, m))
  }

  if (Array.isArray(data.steps)) {
    parts.push('<div class="rc-sec">👨‍🍳 步骤</div>')
    data.steps.forEach(s => {
      if (typeof s === 'object') {
        let sp = `<div class="rc-item">`
        if (s.step) sp += `<strong>步骤 ${s.step}</strong>：`
        sp += escapeHtml(s.action || s.tip || s.title || '')
        if (s.warning) sp += `<br><span class="rc-warning">⚠️ ${escapeHtml(s.warning)}</span>`
        if (s.tip) sp += `<br><span class="rc-note">💡 ${escapeHtml(s.tip)}</span>`
        sp += '</div>'
        parts.push(sp)
      } else {
        parts.push(`<div class="rc-item">${escapeHtml(s)}</div>`)
      }
    })
  }

  const selSteps = data.selection_steps || data.挑选步骤
  if (Array.isArray(selSteps)) {
    parts.push('<div class="rc-sec">🔍 挑选步骤</div>')
    selSteps.forEach(s => {
      if (typeof s === 'object') parts.push(`<div class="rc-guide-step"><div class="rc-guide-title">${escapeHtml(s.step_name || s.步骤名称 || '')}</div><div class="rc-item">${escapeHtml(s.action || s.具体操作 || '')}</div></div>`)
      else parts.push(`<div class="rc-item">· ${escapeHtml(s)}</div>`)
    })
  }

  if (Array.isArray(data.tools)) {
    parts.push('<div class="rc-sec">🔧 工具</div>')
    data.tools.forEach(t => parts.push(`<div class="rc-item">· ${escapeHtml(t)}</div>`))
  }

  if (Array.isArray(data.suggestions)) {
    parts.push('<div class="rc-sec">💡 建议</div>')
    data.suggestions.forEach(s => {
      if (typeof s === 'object') parts.push(`<div class="rc-item"><strong>${escapeHtml(s.item || '')}</strong>：${escapeHtml(s.detail || '')}</div>`)
      else parts.push(`<div class="rc-item">· ${escapeHtml(s)}</div>`)
    })
  }

  if (Array.isArray(data.outfits)) {
    parts.push('<div class="rc-sec">👕 推荐穿搭</div>')
    data.outfits.forEach(o => {
      if (typeof o === 'object') parts.push(`<div class="rc-item">· ${escapeHtml(o.piece || '')}${o.description ? ' - ' + escapeHtml(o.description) : ''}${o.color ? '（' + escapeHtml(o.color) + '）' : ''}</div>`)
      else parts.push(`<div class="rc-item">· ${escapeHtml(o)}</div>`)
    })
  }

  if (Array.isArray(data.do_list)) {
    parts.push('<div class="rc-sec">✅ 应该做</div>')
    data.do_list.forEach(d => renderDoItem(parts, d))
  }
  if (Array.isArray(data.dont_list)) {
    parts.push('<div class="rc-sec">❌ 不应该做</div>')
    data.dont_list.forEach(d => renderDoItem(parts, d))
  }

  const mistakes = data.common_mistakes || data.误區 || data.常见误区
  if (Array.isArray(mistakes)) {
    parts.push('<div class="rc-sec">⚠️ 常见误区</div>')
    mistakes.forEach(m => parts.push(`<div class="rc-item">· ${escapeHtml(m)}</div>`))
  }

  if (Array.isArray(data.key_principles)) {
    parts.push('<div class="rc-sec">📌 关键原则</div>')
    data.key_principles.forEach(p => parts.push(`<div class="rc-item">· ${escapeHtml(p)}</div>`))
  }

  if (Array.isArray(data.color_palette)) {
    parts.push('<div class="rc-sec">🎨 推荐配色</div>')
    data.color_palette.forEach(c => parts.push(`<span class="rc-color-tag">${escapeHtml(c)}</span> `))
  }

  if (data.avoid) parts.push(`<div class="rc-sec">🚫 避免</div><div class="rc-item">${escapeHtml(data.avoid)}</div>`)

  if (data.safety_tip) parts.push(`<div class="rc-tip">⚠️ ${escapeHtml(data.safety_tip)}</div>`)
  if (data.prevention) parts.push(`<div class="rc-item"><strong>预防</strong>：${escapeHtml(data.prevention)}</div>`)
  if (data.professional_advice) parts.push(`<div class="rc-tip">🔧 ${escapeHtml(data.professional_advice)}</div>`)
  if (data.when_to_see_vet) parts.push(`<div class="rc-tip">🏥 ${escapeHtml(data.when_to_see_vet)}</div>`)
  if (data.when_to_see_doctor) parts.push(`<div class="rc-tip">🏥 ${escapeHtml(data.when_to_see_doctor)}</div>`)
  if (data.storage_tip) parts.push(`<div class="rc-item"><strong>保存</strong>：${escapeHtml(data.storage_tip)}</div>`)
  if (data.style) parts.push(`<div class="rc-item"><strong>风格</strong>：${escapeHtml(data.style)}</div>`)
  if (data.occasion) parts.push(`<div class="rc-item"><strong>场合</strong>：${escapeHtml(data.occasion)}</div>`)
  if (data.cultural_notes) parts.push(`<div class="rc-item"><strong>文化差异</strong>：${escapeHtml(data.cultural_notes)}</div>`)
  if (data.pet_type) parts.push(`<div class="rc-item"><strong>宠物</strong>：${escapeHtml(data.pet_type)}</div>`)
  if (data.topic) parts.push(`<div class="rc-item"><strong>主题</strong>：${escapeHtml(data.topic)}</div>`)
  if (data.category) parts.push(`<div class="rc-item"><strong>类别</strong>：${escapeHtml(data.category)}</div>`)

  if (data.tips) {
    if (typeof data.tips === 'string') parts.push(`<div class="rc-tip">💡 ${escapeHtml(data.tips)}</div>`)
    else if (Array.isArray(data.tips)) data.tips.forEach(t => parts.push(`<div class="rc-tip">💡 ${escapeHtml(t)}</div>`))
  }

  if (data.key_point) parts.push(`<div class="rc-item"><strong>🔥 关键</strong>：${escapeHtml(data.key_point)}</div>`)
  if (data.summary_slogan) parts.push(`<div class="rc-tip">📝 ${escapeHtml(data.summary_slogan)}</div>`)
  if (data.disclaimer) parts.push(`<div class="rc-tip">${escapeHtml(data.disclaimer)}</div>`)

  // 食谱推荐：一周计划
  if (Array.isArray(data.weekly_plan)) {
    parts.push('<div class="rc-sec">📅 一周食谱</div>')
    data.weekly_plan.forEach(day => {
      let dayHtml = '<div class="rc-day-plan">'
      dayHtml += '<div class="rc-day-title">' + escapeHtml(day.day || '') + '</div>'
      if (Array.isArray(day.meals)) {
        day.meals.forEach(meal => {
          dayHtml += '<div class="rc-meal">'
          dayHtml += '<span class="rc-meal-type">' + escapeHtml(meal.type || '') + '</span>'
          dayHtml += '<span class="rc-meal-name">' + escapeHtml(meal.name || '') + '</span>'
          if (meal.time) dayHtml += '<span class="rc-meal-time">⏱️ ' + escapeHtml(meal.time) + '</span>'
          if (meal.difficulty) dayHtml += '<span class="rc-meal-diff">' + escapeHtml(meal.difficulty) + '</span>'
          dayHtml += '</div>'
        })
      }
      dayHtml += '</div>'
      parts.push(dayHtml)
    })
  }

  // 食谱推荐：购物清单
  if (Array.isArray(data.shopping_list)) {
    parts.push('<div class="rc-sec">🛒 购物清单</div>')
    data.shopping_list.forEach(cat => {
      let catHtml = '<div class="rc-shop-cat">'
      catHtml += '<div class="rc-shop-title">' + escapeHtml(cat.category || '') + '</div>'
      if (Array.isArray(cat.items)) {
        cat.items.forEach(item => {
          catHtml += '<div class="rc-item">· ' + escapeHtml(item) + '</div>'
        })
      }
      catHtml += '</div>'
      parts.push(catHtml)
    })
  }
  // 兜底：未处理的字段
  for (const [key, val] of Object.entries(data)) {
    if (['title','difficulty','time','servings','ingredients','materials','steps','selection_steps','挑选步骤','tools','suggestions','outfits','do_list','dont_list','common_mistakes','误區','常见误区','key_principles','color_palette','avoid','safety_tip','prevention','professional_advice','when_to_see_vet','when_to_see_doctor','storage_tip','style','occasion','cultural_notes','pet_type','topic','category','tips','key_point','summary_slogan','disclaimer','answer','severity','question','problem','品类','need_professional','season'].includes(key)) continue
    if (typeof val === 'string' && val.length < 200) {
      parts.push(`<div class="rc-item"><strong>${escapeHtml(key)}</strong>：${escapeHtml(val)}</div>`)
    }
  }

  // 生成推荐问题（AI未返回时的默认兜底）
  let recQuestions = data.followUps || []
  if (!recQuestions || !Array.isArray(recQuestions) || recQuestions.length === 0) {
    const kw = (data.title || data.question || data.problem || '').replace(/[、，。]/g, ' ').trim()
    if (kw && kw.length > 1) {
      recQuestions = [kw + '的食材替代', '怎么做' + kw, '怎么挑' + kw]
    }
  }
  if (recQuestions && Array.isArray(recQuestions) && recQuestions.length > 0) {
    parts.push(`<div class="rc-followups"><div class="rc-followup-title">💡 你可能还想问</div>${recQuestions.map((q, i) => `<span class="rc-followup-chip" data-idx="${i}">${escapeHtml(q)}</span>`).join("")}</div>`)
  }
  if (data.answer) {
    parts.push(`<div class="rc-item" style="margin-top:8px">${escapeHtml(data.answer).replace(/\n/g, '<br>')}</div>`)
  }

  return '<div class="rc-card">' + parts.join('') + '</div>'
}

function renderItem(parts, item) {
  if (typeof item === 'object') {
    let s = `· ${escapeHtml(item.name || '')}`
    if (item.amount) s += ` <strong>${escapeHtml(item.amount)}</strong>`
    if (item.note) s += `<br><span class="rc-note">💡 ${escapeHtml(item.note)}</span>`
    parts.push(`<div class="rc-item">${s}</div>`)
  } else {
    parts.push(`<div class="rc-item">· ${escapeHtml(item)}</div>`)
  }
}

function renderDoItem(parts, d) {
  if (typeof d === 'object') {
    parts.push(`<div class="rc-item">· ${escapeHtml(d.action || '')}${d.reason ? '<br><span class="rc-note">原因：' + escapeHtml(d.reason) + '</span>' : ''}</div>`)
  } else {
    parts.push(`<div class="rc-item">· ${escapeHtml(d)}</div>`)
  }
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
.upload-btn { flex-shrink: 0; }

.pending-preview { position: relative; flex-shrink: 0; }
.pending-preview img { height: 40px; border-radius: 6px; border: 1px solid #e0e0e0; }
.pending-remove { position: absolute; top: -6px; right: -6px; width: 18px; height: 18px; background: #ef4444; color: #fff; border-radius: 50%; font-size: 11px; display: flex; align-items: center; justify-content: center; cursor: pointer; line-height: 1; }

/* 图片消息 */
.msg-image { margin-bottom: 8px; cursor: pointer; }
.msg-image img { max-width: 240px; max-height: 200px; border-radius: 10px; border: 1px solid #e0e0e0; display: block; box-shadow: 0 1px 6px rgba(0,0,0,.08); }
.msg-user .msg-image img { border-color: rgba(255,255,255,.3); }

/* 图片预览弹窗 */
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
.rc-card h4 { font-size: 16px; margin: 0 0 4px; }
.rc-tags { font-size: 12px; color: #666; margin-bottom: 10px; }
.rc-sec { font-weight: 600; font-size: 13px; margin: 10px 0 4px; color: #444; }
.rc-item { font-size: 13px; color: #333; margin: 5px 0; padding-left: 2px; line-height: 1.6; }
.rc-note { font-size: 12px; color: #888; display: block; }
.rc-warning { font-size: 12px; color: #dc2626; display: block; }
.rc-tip { background: #fefce8; border-radius: 8px; padding: 10px; font-size: 12px; color: #a16207; margin-top: 10px; line-height: 1.6; }
.rc-guide-step { margin: 8px 0; }
.rc-guide-title { font-weight: 600; font-size: 13px; color: #444; margin-bottom: 4px; }
.rc-color-tag { display: inline-block; background: #f0f0f0; border-radius: 12px; padding: 2px 10px; font-size: 12px; margin: 2px; }
.rc-day-plan { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; padding: 10px; margin: 6px 0; }
.rc-day-title { font-weight: 600; font-size: 14px; color: #1a202c; margin-bottom: 6px; }
.rc-meal { display: flex; align-items: center; gap: 8px; padding: 4px 0; font-size: 13px; border-bottom: 1px solid #e2e8f0; }
.rc-meal:last-child { border-bottom: none; }
.rc-meal-type { background: #22c55e; color: #fff; border-radius: 4px; padding: 1px 6px; font-size: 11px; white-space: nowrap; }
.rc-meal-name { flex: 1; color: #333; }
.rc-meal-time { color: #888; font-size: 12px; white-space: nowrap; }
.rc-meal-diff { color: #999; font-size: 11px; white-space: nowrap; }
.rc-followups { margin-top: 12px; padding-top: 10px; border-top: 1px dashed #e0e0e0; }
.rc-followup-title { font-size: 12px; color: #888; margin-bottom: 6px; }
.rc-followup-chip { display: inline-block; background: #f0fdf4; color: #16a34a; font-size: 12px; padding: 4px 10px; border-radius: 14px; margin: 3px 4px 3px 0; cursor: pointer; border: 1px solid #bbf7d0; transition: .1s; }
.rc-followup-chip:hover { background: #dcfce7; transform: scale(1.02); }

.rc-shop-cat { margin: 6px 0; }
.rc-shop-title { font-weight: 600; font-size: 13px; color: #444; margin-bottom: 4px; }

/* 消息高亮 */
.msg-highlight { animation: hlPulse 2.5s ease; }
@keyframes hlPulse {
  0% { background-color: rgba(34,197,94,0.15); border-radius: 12px; }
  100% { background-color: transparent; }
}
</style>
