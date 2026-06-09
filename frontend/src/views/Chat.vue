<!-- VERSION: 20260606-2 --> <template>
  <div class="page-container chat-page">
    <div class="chat-header">
      <el-button text @click="goBack" class="back-btn">← 返回</el-button>
      <span class="header-title" @click="handleRenameTitle" style="cursor:pointer">{{ currentLabel }}</span>
      <el-button text size="small" @click="toggleExportMenu" title="导出对话">📥</el-button>
      <div v-if="showExportMenu" class="export-menu" @mouseleave="showExportMenu = false">
        <div class="export-menu-item" @click="exportConversation(); showExportMenu = false">📄 导出文本</div>
        <div class="export-menu-item" @click="exportConversationAsImage(); showExportMenu = false">🖼️ 导出图片</div>
        <div class="export-menu-item" @click="exportConversationAsPdf(); showExportMenu = false">📑 导出PDF</div>
      </div>
    </div>

    <div class="messages" ref="msgBox">
      <div v-if="messages.length === 0 && !loading" class="welcome">
        <div class="welcome-icon">🌿</div>
        <div class="welcome-text">问我关于{{ currentLabel }}的问题吧</div>
        <div v-if="welcomePrompts.length" class="welcome-prompts">
          <div class="wp-title">可以这样问</div>
          <div class="wp-grid">
            <button v-for="p in welcomePrompts" :key="p" class="wp-chip" @click="fillPrompt(p)">{{ p }}</button>
          </div>
        </div>
        <div v-if="isWritingScene" class="writing-modes">
          <div class="wm-title">📝 选择写作类型预填问题</div>
          <div class="wm-grid">
            <div v-for="wm in writingModes" :key="wm.key" class="wm-chip" @click="fillPrompt(wm.prompt)">
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
              <img :src="imgUrl(msg.imageUrl)" alt="图片" loading="lazy" @error="onImgError($event)" />
            </div>
            <div v-html="msg._displayHtml || renderContent(msg.content)"></div>
            <div v-if="msg._foodImageLoading" class="food-image-loading">
              <span class="fil-spinner"></span>
              <span class="fil-text">{{ msg._foodImageLoadingText || '🖼️ 正在查找本地成品图…' }}</span>
              <el-button text size="small" type="info" @click="cancelFoodImage(msg)" style="margin-left:auto;flex-shrink:0">取消</el-button>
            </div>
            <div v-if="msg._foodImageUrl" class="food-image" @click="previewImage(msg._foodImageUrl)">
              <img :src="msg._foodImageUrl" alt="成品图" loading="lazy" />
              <span v-if="msg._foodImageSource" class="food-image-badge" :class="{ paid: msg._foodImageSource === 'generated' }">{{ foodImageBadge(msg) }}</span>
            </div>
            <div v-if="msg.role === 'assistant' && msg._foodDishName && !msg._foodImageUrl && !msg._foodImageLoading" class="food-image-actions">
              <span>未找到“{{ msg._foodDishName }}”本地成品图</span>
              <el-button size="small" type="primary" plain @click="confirmGenerateFoodImage(msg)">手动生成（会扣费）</el-button>
            </div>
          </div>
        </div>
        <div v-if="msg.role === 'assistant' && !msg._typing && msg._source === 'knowledge-base'" class="source-badge free">
          ✅ 来自常识库，未调用 AI
        </div>
        <div v-else-if="msg.role === 'assistant' && !msg._typing && msg._externalCall === false" class="source-badge free">
          ✅ {{ msg._sourceLabel || '未调用外部 AI' }}
        </div>
        <div v-if="msg.role === 'assistant' && !msg._typing" class="msg-actions">
          <el-button text size="small" @click="copyMsg(i)">📋 复制</el-button>
          <el-button text size="small" @click="shareMsg(i)">📤 分享</el-button>
          <el-button text size="small" @click="addMsgToKnowledge(i)" :disabled="msg._kbSaving">
            {{ msg._kbSaving ? '⏳ 入库中...' : msg._kbSaved ? '✅ 已入库' : '📚 入库' }}
          </el-button>
          <el-button text size="small" @click="toggleFavorite(i)" :type="msg._faved ? 'warning' : 'default'">
            {{ msg._favProcessing ? '⏳ 处理中...' : msg._faved ? '⭐ 已收藏' : '☆ 收藏' }}
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
        <el-button type="primary" @click="shareToSocial" :loading="shareDialog.sharing">
          📱 分享到社交
        </el-button>
        <el-button @click="copyShareText">
          📋 复制文本
        </el-button>
      </template>
    </el-dialog>

        <!-- 图片快捷操作栏 -->
    <div v-if="uploadProgress > 0 && uploadProgress < 100" class="upload-progress-bar">
        <div class="upb-fill" :style="{ width: uploadProgress + '%' }"></div>
        <span class="upb-text">{{ uploadProgress }}%</span>
      </div>
      <div v-if="pendingImage" class="image-preview-bar">
      <div class="ipb-preview">
        <img :src="pendingImage" />
        <span class="ipb-remove" @click="pendingImage = null; pendingFile = null">✕</span>
      </div>
      <div class="ipb-actions">
        <span class="ipb-chip" @click="fillImagePrompt('请分析这张图片', 'cooking')">🔍 分析图片</span>
        <span class="ipb-chip" @click="fillImagePrompt('请识别这张图片的内容', 'other')">👀 识别内容</span>
        <span class="ipb-chip" @click="fillImagePrompt('请描述这张图片', 'writing')">📝 描述图片</span>
      </div>
    </div>
    <div class="input-area">
      <div v-if="promptPrefilled && inputText.trim() && !loading" class="prefill-hint">
        已填入示例问题，确认无误后点右侧发送
      </div>
      <div class="cost-hint" :class="costHint.type">
        <span class="cost-hint-main">{{ costHint.text }}</span>
        <span class="cost-hint-sub">{{ costHint.sub }}</span>
      </div>
      <div v-if="currentConvId" class="ask-mode-row">
        <button type="button" class="ask-mode-chip" :class="{ active: !pendingFollowUp }" @click="setAskMode(false)" :disabled="loading">
          正常提问
        </button>
        <button type="button" class="ask-mode-chip follow" :class="{ active: pendingFollowUp }" @click="setAskMode(true)" :disabled="loading">
          作为追问
        </button>
        <span class="ask-mode-tip">{{ pendingFollowUp ? '会结合上文口语化补充' : '会按新问题正常回答' }}</span>
      </div>
      <el-button :icon="Microphone" circle size="small" @click="startVoice" :type="isListening ? 'danger' : 'default'" :class="{ 'mic-listening': isListening }" :disabled="loading" />
      <el-button class="upload-btn" :icon="Picture" circle size="small" @click="triggerUpload" :disabled="loading" />
      <input ref="fileInput" type="file" accept="image/*" style="display:none" @change="handleFileSelect" />
      <el-input v-model="inputText" ref="inputRef" placeholder="输入你的问题..." size="large"
                @keyup.enter="send" :disabled="loading" clearable @input="onUserInput" />
      <el-button type="success" :icon="Promotion" circle @click="send" :class="{ 'send-ready': inputText.trim() || pendingFile }" :disabled="loading || !inputText.trim() && !pendingFile" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getConversation, sendChat, addFavorite, removeFavorite, uploadImage, updateFavoriteCategory, lookupFoodImage, generateFoodImage, getFoodImageStatus, saveFoodImageCache, addKnowledge, getAiConfigStatus } from '../api'
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
const promptPrefilled = ref(false)
const pendingFollowUp = ref(false)
const currentTyping = ref(false)
const previewImg = ref(null)
const uploadProgress = ref(0)
const currentConvId = ref(null)
const currentAiProvider = ref(localStorage.getItem('setting_aiProvider') || 'qwen')
const foodImageEnabled = ref(localStorage.getItem('setting_foodImage') !== 'off')
const aiConfigStatus = ref(null)
function previewImage(url) { previewImg.value = url }
function onImgError(e) {
  e.target.style.display = "none"
  const parent = e.target.parentElement
  if (parent) {
    const fallback = document.createElement("div")
    fallback.style.cssText = "padding:20px;text-align:center;color:#999;font-size:13px"
    fallback.textContent = "🖼️ 图片加载失败"
    parent.appendChild(fallback)
  }
}
function cancelFoodImage(m) {
  if (m._foodImagePoll) { clearInterval(m._foodImagePoll); m._foodImagePoll = null }
  m._foodImageLoading = false
  ElMessage.info("已取消生成成品图")
}
const tempScene = ref('')
const shareCardRef = ref(null)
const isListening = ref(false)
const favDialog = ref({ show: false, selected: 'other', msgIndex: -1 })
const shareDialog = ref({ show: false, question: '', html: '', date: '', downloading: false, sharing: false })
const showExportMenu = ref(false)
function toggleExportMenu() { showExportMenu.value = !showExportMenu.value }
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

const prebuiltFoodNames = [
  '鱼香肉丝', '宫保鸡丁', '红烧肉', '糖醋里脊', '麻婆豆腐', '西红柿炒鸡蛋', '番茄炒蛋', '番茄炒鸡蛋',
  '青椒肉丝', '土豆丝', '酸辣土豆丝', '回锅肉', '可乐鸡翅', '红烧排骨', '糖醋排骨', '蒜蓉西兰花',
  '地三鲜', '鱼香茄子', '肉末茄子', '黄焖鸡', '辣子鸡', '清蒸鱼', '红烧鱼', '水煮鱼',
  '酸菜鱼', '水煮肉片', '京酱肉丝', '蚂蚁上树', '干煸豆角', '蒜苔炒肉', '木须肉', '葱爆羊肉',
  '番茄牛腩', '土豆炖牛肉', '小炒黄牛肉', '农家小炒肉', '香菇青菜', '炒青菜', '手撕包菜', '干锅花菜',
  '韭菜炒鸡蛋', '虾仁炒蛋', '油焖大虾', '蒜蓉粉丝虾', '蛋炒饭', '扬州炒饭', '鸡蛋面', '米饭',
  '馒头', '包子', '饺子', '白粥', '汤面', '豆浆', '油条', '馄饨',
  '牛肉面', '烧麦', '粽子', '小笼包', '煎饼果子', '皮蛋瘦肉粥', '炒面', '炸酱面',
  '酸辣粉', '辣椒炒肉', '红烧茄子', '凉拌黄瓜', '紫菜蛋花汤', '醋溜白菜'
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
const welcomePrompts = computed(() => {
  const scene = localStorage.getItem('currentScene') || 'other'
  const map = {
    cooking: ['今晚吃什么比较简单？', '红烧排骨怎么做？', '冰箱剩菜怎么搭配？'],
    shopping: ['怎么挑西瓜？', '买牛肉怎么选？', '哪些水果适合囤？'],
    repair: ['水龙头滴水怎么办？', '灯泡不亮怎么排查？', '马桶堵了先怎么处理？'],
    housework: ['衣服染色怎么办？', '厨房油污怎么清理？', '冰箱异味怎么去除？'],
    health: ['熬夜后怎么恢复？', '久坐腰酸怎么办？', '感冒时饮食注意什么？'],
    fashion: ['面试穿什么合适？', '黑色裤子怎么搭配？', '矮个子怎么显高？'],
    etiquette: ['第一次见家长带什么？', '怎么委婉拒绝别人？', '送礼怎么避免尴尬？'],
    pet: ['猫不爱喝水怎么办？', '狗狗掉毛严重怎么办？', '新手养猫要准备什么？'],
    mealplan: ['帮我安排一周晚餐', '今晚吃什么比较健康？', '两个人做饭怎么搭配？'],
    writing: []
  }
  return map[scene] || ['家里临时有问题怎么处理？', '帮我整理一个解决步骤', '这个生活问题有什么注意事项？']
})
const costHint = computed(() => {
  if (pendingFile.value) {
    const vision = aiConfigStatus.value?.vision
    if (vision?.configured) {
      return {
        type: 'warn',
        text: '图片识别：将调用小米 MiMo 视觉模型',
        sub: '图片会跳过常识库缓存，发送前请确认问题'
      }
    }
    return {
      type: 'danger',
      text: '图片识别未配置完整',
      sub: '请检查 ai.vision-api-url / ai.vision-api-key'
    }
  }
  if (currentAiProvider.value === 'ollama') {
    return {
      type: 'free',
      text: '当前模型：Ollama 本地',
      sub: '本地模型，不扣平台费用'
    }
  }
  if (currentAiProvider.value === 'deepseek') {
    return {
      type: 'danger',
      text: '当前模型：DeepSeek',
      sub: '服务端默认拦截；如开启会产生 DeepSeek 费用'
    }
  }
  return {
    type: 'normal',
    text: '当前模型：千问 Qwen',
    sub: foodImageEnabled.value ? '聊天会消耗千问；菜品图只查本地缓存，不自动生图扣费' : '聊天会消耗千问；命中常识库则不扣费'
  }
})

onMounted(async () => {

  msgBox.value?.addEventListener('click', handleFollowUpClick)
  refreshLocalSettings()
  loadAiConfigStatus()
  window.addEventListener('focus', refreshLocalSettings)
  window.addEventListener('storage', refreshLocalSettings)
  await nextTick()
  inputRef.value?.focus()
  if (route.params.id) { currentConvId.value = Number(route.params.id); await loadConversation(route.params.id) }
  else {
    const pendingPrompt = localStorage.getItem('pendingPrompt')
    if (pendingPrompt) {
      inputText.value = pendingPrompt
      promptPrefilled.value = true
      localStorage.removeItem('pendingPrompt')
      await nextTick()
      inputRef.value?.focus()
    }
  }
})
onUnmounted(() => {
  msgBox.value?.removeEventListener('click', handleFollowUpClick)
  window.removeEventListener('focus', refreshLocalSettings)
  window.removeEventListener('storage', refreshLocalSettings)
  messages.value.forEach(m => {
    if (m._foodImagePoll) {
      clearInterval(m._foodImagePoll)
      m._foodImagePoll = null
    }
  })
})

function refreshLocalSettings() {
  currentAiProvider.value = localStorage.getItem('setting_aiProvider') || 'qwen'
  foodImageEnabled.value = localStorage.getItem('setting_foodImage') !== 'off'
}

async function loadAiConfigStatus() {
  try {
    const res = await getAiConfigStatus()
    aiConfigStatus.value = res.data || null
  } catch {
    aiConfigStatus.value = null
  }
}

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
    hydrateFoodImagesForLoadedMessages()
    await nextTick(); scrollBottom()
  } catch(e) { ElMessage.error('加载消息失败') }
  finally { loading.value = false }
}

async function send() {
  const msg = inputText.value.trim()
  if (!msg && !pendingFile.value) return
  const isFollowUp = pendingFollowUp.value
  pendingFollowUp.value = false

  refreshLocalSettings()
  if (currentAiProvider.value === 'deepseek') {
    const allowUntil = Number(localStorage.getItem('allow_deepseek_until') || 0)
    if (allowUntil > Date.now()) {
      // User has explicitly confirmed DeepSeek in Profile recently.
    } else {
    localStorage.setItem('setting_aiProvider', 'qwen')
    currentAiProvider.value = 'qwen'
    ElMessage.warning('DeepSeek 已被前端拦截并切回千问，避免误扣费')
    return
    }
  }

  const scene = tempScene.value || localStorage.getItem('currentScene') || 'other'; tempScene.value = ''
  let convId = currentConvId.value
  let imageUrl = ''

  if (pendingFile.value) {
    try {
      const uploadRes = await uploadImage(pendingFile.value, (e) => { uploadProgress.value = Math.round((e.loaded / e.total) * 100) })
      imageUrl = uploadRes.data?.url || ""
      pendingFile.value = null; pendingImage.value = null
    } catch(e) {
      uploadProgress.value = 0
      if (e?.response?.status === 401) {
        ElMessage.error('登录已过期，请重新登录后再上传图片')
      } else if (e?.response?.status === 413) {
        ElMessage.error('图片太大，请压缩到 10MB 以内')
      } else if (e?.response?.data?.message) {
        ElMessage.error(e.response.data.message)
      } else if (e?.message?.includes('Network')) {
        ElMessage.error('无法连接服务器，请检查后端是否启动')
      } else {
        ElMessage.error('图片上传失败，请换一张图片重试')
      }
      return
    }
  }

  messages.value.push({ _id: 'user-' + Date.now(), role: 'user', content: msg, imageUrl, _typing: false, _displayHtml: '', _faved: false })
  inputText.value = ''
  promptPrefilled.value = false
  await nextTick(); scrollBottom()

  loading.value = true; currentTyping.value = true
  const aiIdx = messages.value.length
  messages.value.push({ _id: 'ai-' + Date.now(), role: 'assistant', content: '', imageUrl: '', _typing: true, _displayHtml: '', _faved: false })

  try {
    const res = await sendChat(msg, scene, convId, imageUrl, { followUp: isFollowUp })
    const m = messages.value[aiIdx]
    if (res.data?.id && m) { m._id = res.data.id }
    if (res.data?.conversationId) currentConvId.value = res.data.conversationId
    const fullContent = typeof res.data === "string" ? res.data : (res.data?.content || res.data?.answer || JSON.stringify(res.data))
    if (m) {
      m._typing = false; m.content = fullContent
      m._source = res.data?.source || ''
      m._externalCall = res.data?.externalCall
      m._sourceLabel = res.data?.sourceLabel || ''
      m._displayHtml = renderContent(fullContent)
      currentTyping.value = false; scrollBottom()
      // Recipe image: only lookup local/server cache automatically; never auto-submit paid generation.
      const foodImageEnabled = localStorage.getItem('setting_foodImage') !== 'off'
      if (foodImageEnabled) try {
        const dishName = detectDishName(fullContent, msg)
        attachLocalFoodImage(m, dishName)
      } catch {}
    }
  } catch(e) {
    const m = messages.value[aiIdx]
    if (m) {
      let errMsg = "请求出错了，请稍后再试"
      if (e?.response?.status === 502) errMsg = "AI 服务暂时不可用，请稍后再试"
      else if (e?.response?.status === 401) errMsg = "登录已过期，请重新登录"
      else if (e?.message?.includes("Network")) errMsg = "无法连接服务器，请检查后端是否启动"
      m._typing = false; m._displayHtml = '<div style="color:#ef4444;padding:8px">' + errMsg + '</div>'; currentTyping.value = false
    }
  } finally { loading.value = false }
}


function attachLocalFoodImage(message, dishName) {
  if (!message || !dishName || message._foodImageUrl || message._foodImageLoading) return
  message._foodDishName = dishName
  const dishKey = 'food_img_' + dishName
  const cached = localStorage.getItem(dishKey)
  if (cached) {
    message._foodImageUrl = cached
    message._foodImageSource = 'local-prebuilt'
    return
  }
  message._foodImageLoading = true
  message._foodImageLoadingText = '🖼️ 正在查找本地/缓存成品图…'
  lookupFoodImage(dishName).then(res => {
    const data = res.data || {}
    if (data.found && data.imageUrl) {
      message._foodImageUrl = data.imageUrl
      message._foodImageSource = data.source || 'local-prebuilt'
      localStorage.setItem(dishKey, data.imageUrl)
      nextTick(scrollBottom)
    }
    message._foodImageLoading = false
    message._foodImageLoadingText = ''
  }).catch(() => {
    message._foodImageLoading = false
    message._foodImageLoadingText = ''
  })
}

function foodImageBadge(message) {
  if (message._foodImageSource === 'generated') return 'AI生成 · 已扣费'
  if (message._foodImageSource === 'remote-cache') return '服务器缓存 · 不重复扣费'
  return '本地缓存 · 不扣费'
}

async function confirmGenerateFoodImage(message) {
  const dishName = message?._foodDishName
  if (!dishName || message._foodImageLoading) return
  try {
    await ElMessageBox.confirm(
      `将为“${dishName}”调用外部生图接口，可能产生费用。是否继续？`,
      '手动生成菜品图',
      { confirmButtonText: '确认生成', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  await startFoodImageGeneration(message, dishName)
}

async function startFoodImageGeneration(message, dishName) {
  try {
    message._foodImageLoading = true
    message._foodImageLoadingText = '🎨 正在提交生图任务…'
    const res = await generateFoodImage(dishName)
    if (res.code && res.code !== 200) throw new Error(res.message || '生图提交失败')
    const data = res.data || {}
    if (data.imageUrl) {
      applyFoodImage(message, dishName, data.imageUrl, data.source || 'remote-cache')
      return
    }
    if (!data.taskId) throw new Error('生图服务未返回任务 ID')
    message._foodImageTaskId = data.taskId
    pollFoodImageStatus(message, dishName, data.taskId)
  } catch (e) {
    message._foodImageLoading = false
    message._foodImageLoadingText = ''
    ElMessage.error(e?.message || '生图失败，请稍后再试')
  }
}

function pollFoodImageStatus(message, dishName, taskId) {
  let attempts = 0
  if (message._foodImagePoll) clearInterval(message._foodImagePoll)
  message._foodImageLoadingText = '🎨 正在生成成品图…'
  message._foodImagePoll = setInterval(async () => {
    attempts += 1
    try {
      const res = await getFoodImageStatus(taskId)
      if (res.code && res.code !== 200) throw new Error(res.message || '查询生图状态失败')
      const data = res.data || {}
      if (data.progress) message._foodImageLoadingText = `🎨 正在生成成品图…${data.progress}`
      if (data.isFinal) {
        clearInterval(message._foodImagePoll)
        message._foodImagePoll = null
        if (data.resultUrl) {
          applyFoodImage(message, dishName, data.resultUrl, 'generated')
          saveFoodImageCache(dishName, data.resultUrl).catch(() => {})
          ElMessage.success('成品图已生成')
        } else {
          message._foodImageLoading = false
          message._foodImageLoadingText = ''
          ElMessage.warning('生图已结束，但没有返回图片')
        }
      }
      if (attempts >= 60) {
        clearInterval(message._foodImagePoll)
        message._foodImagePoll = null
        message._foodImageLoading = false
        message._foodImageLoadingText = ''
        ElMessage.warning('生图时间较长，请稍后再试')
      }
    } catch (e) {
      clearInterval(message._foodImagePoll)
      message._foodImagePoll = null
      message._foodImageLoading = false
      message._foodImageLoadingText = ''
      ElMessage.error(e?.message || '查询生图状态失败')
    }
  }, 2500)
}

function applyFoodImage(message, dishName, imageUrl, source) {
  message._foodImageUrl = imageUrl
  message._foodImageSource = source
  message._foodImageLoading = false
  message._foodImageLoadingText = ''
  localStorage.setItem('food_img_' + dishName, imageUrl)
  nextTick(scrollBottom)
}

function hydrateFoodImagesForLoadedMessages() {
  if (localStorage.getItem('setting_foodImage') === 'off') return
  messages.value.forEach((m, i) => {
    if (m.role !== 'assistant') return
    const prev = [...messages.value].slice(0, i).reverse().find(item => item.role === 'user')
    const dishName = detectDishName(m.content, prev?.content || '')
    attachLocalFoodImage(m, dishName)
  })
}

function copyMsg(i) {
  const text = messages.value[i]?.content || ''
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success({ message: '✅ 已复制到剪贴板', duration: 1200, offset: 80 })
  }).catch(() => {
    const ta = document.createElement('textarea'); ta.value = text
    document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta)
    ElMessage.success({ message: '✅ 已复制到剪贴板', duration: 1200, offset: 80 })
  })
}

async function exportConversation() {
  const msgs = messages.value
  if (!msgs.length) { ElMessage.info('没有可导出的消息'); return }
  
  let text = 'LifeWise AI \u751f\u6d3b\u52a9\u624b - \u5bf9\u8bdd\u8bb0\u5f55\n'
  text += '\u573a\u666f\uff1a' + (localStorage.getItem('sceneLabel') || '\u751f\u6d3b\u5e38\u8bc6') + '\n'
  text += '\u5bfc\u51fa\u65f6\u95f4\uff1a' + new Date().toLocaleString('zh-CN') + '\n'
  text += '='.repeat(40) + '\n\n'
  
  for (const msg of msgs) {
    if (msg.role === 'user') {
      text += '\ud83d\ude4b \u6211\uff1a' + (msg.content || '(\u56fe\u7247)') + '\n\n'
    } else if (msg.role === 'assistant' && msg.content) {
      let answer = msg.content
      try {
        const parsed = JSON.parse(answer)
        if (parsed.title) answer = parsed.title + '\n' + (parsed.answer || '')
        if (parsed.question) answer = parsed.question + '\n' + answer
      } catch(e) {}
      answer = answer.replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim()
      text += '\ud83e\udd16 AI\uff1a' + answer + '\n\n'
    }
  }
  
  text += '='.repeat(40) + '\n'
  text += '\u7531 LifeWise AI \u751f\u6d3b\u52a9\u624b\u751f\u6210\n'
  
   try {
    // 直接下载到本地，不经过后端
    const blob = new Blob([text], { type: "text/plain;charset=utf-8" })
    const url = URL.createObjectURL(blob)
    const a = document.createElement("a")
    a.href = url
    const now = new Date()
    const ts2 = now.getFullYear() + String(now.getMonth()+1).padStart(2,"0") + String(now.getDate()).padStart(2,"0") + "_" + String(now.getHours()).padStart(2,"0") + String(now.getMinutes()).padStart(2,"0")
    a.download = "LifeWise对话_" + ts2 + ".txt"
    document.body.appendChild(a); a.click(); document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success("对话已下载到本地")
  } catch(e) {
    ElMessage.error("导出失败: " + (e.message || "未知错误"))
  }}

/**
 * 导出对话为图片（长截图）
 */
async function exportConversationAsImage() {
  const msgs = messages.value
  if (!msgs.length) { ElMessage.info('没有可导出的消息'); return }
  
  try {
    const html2canvas = (await import('html2canvas')).default
    
    // Build a temporary offscreen container with the rendered messages
    const wrapper = document.createElement('div')
    wrapper.style.cssText = 'position:fixed;left:-9999px;top:0;width:420px;padding:20px;background:#fff;font-family:-apple-system,system-ui,sans-serif;font-size:14px;line-height:1.6;z-index:999999'
    document.body.appendChild(wrapper)
    
    // Header
    const hdr = document.createElement('div')
    hdr.style.cssText = 'text-align:center;padding:16px 0;border-bottom:2px solid #22c55e;margin-bottom:16px'
    hdr.innerHTML = '<div style="font-size:20px;font-weight:700;color:#16a34a">🌿 LifeWise · AI 生活助手</div>' +
      '<div style="font-size:12px;color:#999;margin-top:4px">' + (localStorage.getItem('sceneLabel') || '生活常识') + ' · ' + new Date().toLocaleString('zh-CN') + '</div>'
    wrapper.appendChild(hdr)
    
    // Messages
    for (const msg of msgs) {
      const div = document.createElement('div')
      div.style.cssText = 'margin-bottom:16px;padding:12px 14px;border-radius:12px;max-width:95%'
      
      if (msg.role === 'user') {
        div.style.cssText += ';background:#f0fdf4;margin-left:auto;text-align:right;border:1px solid #bbf7d0'
        div.innerHTML = '<div style="font-size:13px;color:#666;margin-bottom:4px">🙋 我</div>' +
          '<div style="color:#1a1a1a">' + (msg.content || '(图片)') + '</div>'
      } else if (msg.role === 'assistant' && msg.content) {
        div.style.cssText += ';background:#fff;border:1px solid #e5e7eb;margin-right:auto'
        // Use the rendered HTML if available, or the content directly
        let html = msg._displayHtml || msg.content
        // Remove action parts that shouldn't render in export
        const t = document.createElement('div'); t.innerHTML = html
        t.querySelectorAll('.rc-followups, .msg-actions, .food-image-loading')?.forEach(el => el.remove())
        html = t.innerHTML
        div.innerHTML = '<div style="font-size:13px;color:#666;margin-bottom:4px">🤖 AI</div>' + html
        // Show food image if available
        if (msg._foodImageUrl) {
          div.innerHTML += '<div style="margin-top:8px"><img src="' + msg._foodImageUrl + '" style="max-width:100%;border-radius:8px" /><div style="font-size:11px;color:#16a34a;margin-top:4px">本地缓存 · 不扣费</div></div>'
        }
      }
      wrapper.appendChild(div)
    }
    
    // Footer
    const ftr = document.createElement('div')
    ftr.style.cssText = 'text-align:center;padding:12px 0;border-top:1px solid #e5e7eb;margin-top:8px;color:#999;font-size:12px'
    ftr.textContent = '由 LifeWise AI 生活助手生成'
    wrapper.appendChild(ftr)
    
    await nextTick()
    
    const canvas = await html2canvas(wrapper, {
      scale: 2,
      backgroundColor: '#ffffff',
      useCORS: true,
      logging: false,
      width: wrapper.scrollWidth,
      height: wrapper.scrollHeight
    })
    
    document.body.removeChild(wrapper)
    
    // Download the image
    const link = document.createElement('a')
    link.download = 'lifewise-conversation-' + Date.now() + '.png'
    link.href = canvas.toDataURL('image/png')
    link.click()
    ElMessage.success('对话图片已下载')
  } catch (e) {
    console.error('[exportImage] err:', e)
    ElMessage.error('导出图片失败: ' + (e.message || '未知错误'))
  }
}

/**
 * 导出对话为 PDF
 */
async function exportConversationAsPdf() {
  const msgs = messages.value
  if (!msgs.length) { ElMessage.info('没有可导出的消息'); return }
  
  try {
    const html2canvas = (await import('html2canvas')).default
    const { jsPDF } = await import('jspdf')
    
    // Build same offscreen container as image export
    const wrapper = document.createElement('div')
    wrapper.style.cssText = 'position:fixed;left:-9999px;top:0;width:420px;padding:20px;background:#fff;font-family:-apple-system,system-ui,sans-serif;font-size:14px;line-height:1.6;z-index:999999'
    document.body.appendChild(wrapper)
    
    // Header
    const hdr = document.createElement('div')
    hdr.style.cssText = 'text-align:center;padding:16px 0;border-bottom:2px solid #22c55e;margin-bottom:16px'
    hdr.innerHTML = '<div style="font-size:20px;font-weight:700;color:#16a34a">🌿 LifeWise · AI 生活助手</div>' +
      '<div style="font-size:12px;color:#999;margin-top:4px">' + (localStorage.getItem('sceneLabel') || '生活常识') + ' · ' + new Date().toLocaleString('zh-CN') + '</div>'
    wrapper.appendChild(hdr)
    
    for (const msg of msgs) {
      const div = document.createElement('div')
      div.style.cssText = 'margin-bottom:16px;padding:12px 14px;border-radius:12px;max-width:95%'
      
      if (msg.role === 'user') {
        div.style.cssText += ';background:#f0fdf4;margin-left:auto;text-align:right;border:1px solid #bbf7d0'
        div.innerHTML = '<div style="font-size:13px;color:#666;margin-bottom:4px">🙋 我</div>' +
          '<div style="color:#1a1a1a">' + (msg.content || '(图片)') + '</div>'
      } else if (msg.role === 'assistant' && msg.content) {
        div.style.cssText += ';background:#fff;border:1px solid #e5e7eb;margin-right:auto'
        let html = msg._displayHtml || msg.content
        const t = document.createElement('div'); t.innerHTML = html
        t.querySelectorAll('.rc-followups, .msg-actions, .food-image-loading')?.forEach(el => el.remove())
        html = t.innerHTML
        div.innerHTML = '<div style="font-size:13px;color:#666;margin-bottom:4px">🤖 AI</div>' + html
        if (msg._foodImageUrl) {
          div.innerHTML += '<div style="margin-top:8px"><img src="' + msg._foodImageUrl + '" style="max-width:100%;border-radius:8px" /><div style="font-size:11px;color:#16a34a;margin-top:4px">本地缓存 · 不扣费</div></div>'
        }
      }
      wrapper.appendChild(div)
    }
    
    const ftr = document.createElement('div')
    ftr.style.cssText = 'text-align:center;padding:12px 0;border-top:1px solid #e5e7eb;margin-top:8px;color:#999;font-size:12px'
    ftr.textContent = '由 LifeWise AI 生活助手生成'
    wrapper.appendChild(ftr)
    
    await nextTick()
    
    const canvas = await html2canvas(wrapper, {
      scale: 2,
      backgroundColor: '#ffffff',
      useCORS: true,
      logging: false,
      width: wrapper.scrollWidth,
      height: wrapper.scrollHeight
    })
    
    document.body.removeChild(wrapper)
    
    // Convert canvas to image and add to PDF
    const imgData = canvas.toDataURL('image/png')
    const imgWidth = 210 // A4 width in mm
    const imgHeight = (canvas.height / canvas.width) * imgWidth
    
    const pdf = new jsPDF('p', 'mm', 'a4')
    let heightLeft = imgHeight
    let position = 0
    const pageHeight = 297 // A4 height in mm
    
    // If content fits in one page
    if (imgHeight <= pageHeight) {
      pdf.addImage(imgData, 'PNG', 0, 0, imgWidth, imgHeight)
    } else {
      // Split across multiple pages
      pdf.addImage(imgData, 'PNG', 0, position, imgWidth, imgHeight)
      heightLeft -= pageHeight
      while (heightLeft > 0) {
        position -= pageHeight
        pdf.addPage()
        pdf.addImage(imgData, 'PNG', 0, position, imgWidth, imgHeight)
        heightLeft -= pageHeight
      }
    }
    
    pdf.save('lifewise-conversation-' + Date.now() + '.pdf')
    ElMessage.success('PDF 已下载')
  } catch (e) {
    console.error('[exportPdf] err:', e)
    ElMessage.error('导出PDF失败: ' + (e.message || '未知错误'))
  }
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
    msg._favProcessing = true
    try { await removeFavorite(msg._id); msg._faved = false; msg._favProcessing = false; ElMessage.success('已取消收藏') }
    catch(e) { msg._favProcessing = false; ElMessage.error(e?.response?.data?.message || '操作失败，请重试') }
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
  } catch(e) { console.error('[favorite] err:',e); ElMessage.error('收藏失败，请重试') }
}

async function addMsgToKnowledge(i) {
  const msg = messages.value[i]
  if (!msg || msg.role !== 'assistant') return
  const prev = [...messages.value].slice(0, i).reverse().find(m => m.role === 'user')
  if (!prev?.content) {
    ElMessage.warning('没有找到对应的问题')
    return
  }
  msg._kbSaving = true
  try {
    await addKnowledge({
      question: prev.content,
      answer: msg.content || '',
      scene: localStorage.getItem('scene') || tempScene.value || 'other'
    })
    msg._kbSaved = true
    ElMessage.success('已加入常识库')
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '入库失败')
  } finally {
    msg._kbSaving = false
  }
}

async function shareToSocial() {
  const d = shareDialog.value
  if (!d) return
  shareDialog.value.sharing = true
  
  try {
    // Build formatted share text
    let text = '?? LifeWise \u00b7 AI \u751f\u6d3b\u52a9\u624b\n'
    if (d.question) text += '\ud83d\udcac ' + d.question + '\n'
    text += '\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n'
    const temp = document.createElement('div')
    temp.innerHTML = d.html
    text += (temp.textContent || temp.innerText || '').trim()
    text += '\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n'
    text += 'via LifeWise'
    
    // Try Web Share API first (mobile native share - supports WeChat, WhatsApp, etc.)
    if (navigator.share) {
      const shareData = { title: 'LifeWise', text }
      if (route.params.id) {
        shareData.url = window.location.origin + '/chat/' + route.params.id
      }
      
      // Try to include image
      try {
        const el = shareCardRef.value
        if (el) {
          const html2canvas = (await import('html2canvas')).default
          const canvas = await html2canvas(el, { scale: 2, backgroundColor: '#ffffff', useCORS: true })
          const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/png'))
          if (blob && navigator.canShare && navigator.canShare({ files: [new File([blob], 'lifewise.png', { type: 'image/png' })] })) {
            shareData.files = [new File([blob], 'lifewise.png', { type: 'image/png' })]
          }
        }
      } catch {}
      
      try {
        await navigator.share(shareData)
        ElMessage.success('\u5df2\u6253\u5f00\u5206\u4eab\u9762\u677f')
        shareDialog.value.show = false
        return
      } catch (e) {
        if (e.name !== 'AbortError') {
          // Web Share failed, fall through to fallback
        } else {
          shareDialog.value.sharing = false
          return // User cancelled
        }
      }
    }
    
    // Fallback: copy text and notify
    navigator.clipboard.writeText(text).then(() => {
      ElMessage.success('\u6587\u672c\u5df2\u590d\u5236\uff0c\u53ef\u4ee5\u7c98\u8d34\u5230\u5fae\u4fe1\u3001\u5fae\u535a\u7b49\u793e\u4ea4\u5e73\u53f0')
    }).catch(() => {
      const ta = document.createElement('textarea'); ta.value = text
      document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta)
      ElMessage.success('\u6587\u672c\u5df2\u590d\u5236')
    })
  } finally {
    shareDialog.value.sharing = false
  }
}


async function fillPrompt(prompt) {
  inputText.value = prompt
  promptPrefilled.value = true
  await nextTick()
  inputRef.value?.focus()
}

async function fillImagePrompt(prompt, scene) {
  tempScene.value = scene
  await fillPrompt(prompt)
}

function onUserInput() {
  promptPrefilled.value = false
}

function setAskMode(isFollowUp) {
  pendingFollowUp.value = isFollowUp
}

function handleFollowUpClick(e) {
  const chip = e.target.closest('.rc-followup-chip')
  if (chip) {
    pendingFollowUp.value = true
    inputText.value = chip.textContent
    send()
  }
}

function triggerUpload() { fileInput.value?.click() }
function handleFileSelect(e) {
  const file = e.target.files?.[0]; if (!file) return
  const maxSize = 10 * 1024 * 1024
  if (!file.type?.startsWith('image/')) {
    ElMessage.warning('只能上传图片文件')
    e.target.value = ''
    return
  }
  if (file.size > maxSize) {
    ElMessage.warning('图片不能超过 10MB，请压缩后再上传')
    e.target.value = ''
    return
  }
  pendingFile.value = file
  if (!inputText.value.trim()) {
    inputText.value = '请分析这张图片'
  }
  const reader = new FileReader()
  reader.onload = (ev) => { pendingImage.value = ev.target.result }
  reader.onerror = () => {
    pendingFile.value = null
    pendingImage.value = null
    ElMessage.error('图片读取失败，请换一张图片')
  }
  reader.readAsDataURL(file); e.target.value = ''
}
function imgUrl(url) {
  if (!url) return ''
  if (url.startsWith('http') || url.startsWith('data:')) return url
  if (url.startsWith('/')) return url
  return '/uploads/' + url
}
function startVoice() {
  // If already listening, stop manually
  if (isListening.value) {
    if (window._voiceRecognition) {
      try { window._voiceRecognition.stop() } catch {}
      window._voiceRecognition = null
    }
    isListening.value = false
    ElMessage.info('⏹️ 已停止语音输入')
    return
  }

  if (!('webkitSpeechRecognition' in window) && !('SpeechRecognition' in window)) {
    ElMessage.warning('当前浏览器不支持语音识别，建议用 Chrome 浏览器打开 http://localhost:5173')
    return
  }

  // Check permission first
  if (navigator.permissions) {
    navigator.permissions.query({ name: 'microphone' }).then(result => {
      if (result.state === 'denied') {
        ElMessage.error('❌ 麦克风权限已被禁用，请在浏览器地址栏左侧点击 🔒 或 ⓘ 图标，开启"麦克风"权限后刷新页面重试')
        return
      }
    }).catch(() => {})
  }

  // Stop any previous instance first
  if (window._voiceRecognition) {
    try { window._voiceRecognition.abort() } catch {}
    window._voiceRecognition = null
  }

  // Create new instance
  const r = new (window.webkitSpeechRecognition || window.SpeechRecognition)()
  window._voiceRecognition = r
  r.lang = 'zh-CN'
  r.continuous = true  // continuous mode — 说完自动结束更自然
  r.interimResults = true

  isListening.value = true
  let retryCount = 0
  const maxRetries = 2

  ElMessage.info('🎤 请说话... (点击麦克风可手动停止)')

  r.onresult = (e) => {
    let t = ''
    for (let i = e.resultIndex; i < e.results.length; i++) {
      t += e.results[i][0].transcript
    }
    inputText.value = t
  }

  r.onerror = (e) => {
    if (e.error === 'not-allowed') {
      isListening.value = false; window._voiceRecognition = null
      ElMessage.error('❌ 麦克风被拒绝，请在浏览器地址栏左侧点击 🔒 开启麦克风权限后刷新页面')
    } else if (e.error === 'no-speech') {
      if (retryCount < maxRetries) {
        retryCount++
        ElMessage.info(`🔁 没听到，第${retryCount}次重试...`)
        setTimeout(() => { try { r.start() } catch {} }, 500)
      } else {
        isListening.value = false; window._voiceRecognition = null
        ElMessage.warning('没听到说话，请检查麦克风是否正常后点击麦克风重试')
      }
    } else if (e.error === 'aborted') {
      // Only show error if we didn't intentionally abort
      if (window._voiceRecognition) {
        isListening.value = false; window._voiceRecognition = null
        ElMessage.warning('⚠️ 语音被中断，点击麦克风重试')
      }
    } else {
      isListening.value = false; window._voiceRecognition = null
      ElMessage.error('语音识别失败: ' + e.error)
    }
  }

  r.onend = () => {
    isListening.value = false
    if (window._voiceRecognition) {
      window._voiceRecognition = null
      if (inputText.value.trim()) {
        setTimeout(() => send(), 300)
      }
    }
  }

  try {
    r.start()
  } catch (e) {
    isListening.value = false; window._voiceRecognition = null
    ElMessage.error('启动语音识别失败: ' + e.message)
  }
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

function detectDishName(aiContent, userQuestion = '') {
  const parsed = tryParseJsonSafe(aiContent)
  if (parsed && parsed.title && (parsed.steps || parsed.ingredients)) {
    return String(parsed.title).trim()
  }
  const text = `${userQuestion || ''}\n${aiContent || ''}`
  return prebuiltFoodNames.find(name => text.includes(name)) || ''
}

function renderMarkdown(text) {
  let html = (text || "");
  html = html.replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;")
  // Code blocks (must be before inline code)
  html = html.replace(/```(\w*)\n?([\s\S]*?)```/g, '<pre class="md-code-block"><code class="lang-$1">$2</code></pre>')
  // Inline code
  html = html.replace(/`([^`]+)`/g, "<code>$1</code>")
  // Headings
  html = html.replace(/^#### (.+)$/gm, "<h5>$1</h5>")
    .replace(/^### (.+)$/gm, "<h4>$1</h4>")
    .replace(/^## (.+)$/gm, "<h3>$1</h3>")
    .replace(/^# (.+)$/gm, "<h2>$1</h2>")
  // Tables: | col1 | col2 | ... | (need to handle before line breaks)
  html = html.replace(/^\|(.+)\|$/gm, function(m){ return '<tr>' + m.slice(1,-1).split('|').map(function(c){ return '<td>' + c.trim() + '</td>' }).join('') + '</tr>' })
  html = html.replace(/<tr>\s*<td>[-:\s]+<\/td>(?:\s*<td>[-:\s]+<\/td>)+\s*<\/tr>/g, '')
  html = html.replace(/(<tr>.*?<\/tr>\n?)+/g, '<table class="md-table">$1</table>')
  // Links: [text](url)
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>')
  // Bold & italic
  html = html.replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/\*(.+?)\*/g, "<em>$1</em>")
  // Blockquote
  html = html.replace(/^> (.+)$/gm, "<blockquote>$1</blockquote>")
  // Horizontal rule
  html = html.replace(/^(?:---|\*\*\*|___)\s*$/gm, "<hr>")
  // Unordered list
  html = html.replace(/^[\s]*[-*+]\s+(.+)$/gm, "<li>$1</li>")
  // Ordered list
  html = html.replace(/^[\s]*\d+\.\s+(.+)$/gm, "<li>$1</li>")
  // Wrap consecutive <li> in <ul>
  html = html.replace(/((?:<li>.*?<\/li>\n?)+)/g, '<ul class="md-list">$1</ul>')
  // Convert line breaks (but not inside pre/code/table)
  html = html.replace(/\n/g, "<br>")
  // Cleanup: remove <br> before/after block elements
  html = html.replace(/<br><\/(h[2345]|ul|ol|blockquote|table|pre)>/g, "</$1>")
    .replace(/<(h[2345]|ul|ol|blockquote|table|pre)><br>/g, "<$1>")
    .replace(/<\/li><br>/g, "</li>")
    .replace(/<br><li>/g, "<li>")
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
      const stepKw = s.step_image || (s.action || "").substring(0, 30)
      const stepImg = makeStepImg(stepKw)
      const tip = s.tip ? `<span class="rc-note">💡 ${esc(s.tip)}</span>` : ''
      const warning = s.warning ? `<span class="rc-warning">⚠️ ${esc(s.warning)}</span>` : ''
      parts.push(`<div class="rc-step"><div class="rc-step-badge">${s.step || ''}</div><div class="rc-step-body">${stepImg}<div class="rc-step-text">${esc(s.action || s)}${tip}${warning}</div></div></div>`)
    })
  }
  if (data.selection_steps) {
    parts.push('<div class="rc-sec">🔍 挑选步骤</div>')
    data.selection_steps.forEach(s => {
      const stepKw = s.step_image || (s.action || "").substring(0, 30)
      const stepImg = makeStepImg(stepKw)
      parts.push('<div class="rc-step"><div class="rc-step-badge"></div><div class="rc-step-body">' + stepImg + '<div class="rc-step-text"><strong>' + esc(s.step_name) + '</strong>：' + esc(s.action) + '</div></div></div>')
    })
  }
  if (data.tools) {
    parts.push('<div class="rc-sec">🔧 所需工具</div>')
    data.tools.forEach(t => parts.push(`<div class="rc-item">· ${esc(t)}</div>`))
  }
  if (data.key_point) parts.push(`<div class="rc-tip">🔥 ${esc(data.key_point)}</div>`)
  if (data.safety_tip) parts.push(`<div class="rc-tip" style="color:#dc2626">⚠️ ${esc(data.safety_tip)}</div>`)
  if (data.answer) parts.push(`<div class="rc-item" style="margin-top:8px">${esc(data.answer).replace(/\n/g, '<br>')}</div>`)
  if (data.suggestions) {
    parts.push('<div class="rc-sec">💡 建议</div>')
    const sa = Array.isArray(data.suggestions) ? data.suggestions : [data.suggestions]
    sa.forEach(s => {
      if (typeof s === 'string') { parts.push('<div class="rc-item">· ' + esc(s) + '</div>'); return }
      const sImg = s.step_image ? '<div class="rc-step-img" style="margin:2px 0">' + makeStepImg(s.step_image) + '</div>' : ''
      parts.push('<div class="rc-item">' + sImg + '<strong>' + esc(s.item) + '</strong>：' + esc(s.detail) + '</div>')
    })
  }
  // === extra field handlers ===
  if (data.style) {
    parts.push('<div class="rc-sec">🎨 风格定位</div>')
    parts.push('<div class="rc-item" style="font-size:15px;font-weight:500;color:#111">' + esc(data.style) + '</div>')
  }
  if (data.items && Array.isArray(data.items)) {
    parts.push('<div class="rc-sec">🧥 单品推荐</div>')
    data.items.forEach(function(it) {
      var name = it.name || it.item || ""
      var desc = it.description || it.recommendation || it.detail || it.note || ""
      parts.push('<div class="rc-item">\u00b7 <strong>' + esc(name) + '</strong>' + (desc ? " \u2014 " + esc(desc) : "") + '</div>')
    })
  }
  if (data.outfits && Array.isArray(data.outfits)) {
    parts.push('<div class="rc-sec">👔 搭配方案</div>')
    data.outfits.forEach(function(o) {
      var name = o.piece || o.name || o.occasion || ""
      var desc = o.description || o.detail || ""
      if (Array.isArray(o.items)) desc = o.items.join(", ")
      const pImg = o.step_image ? '<div class="rc-step-img" style="margin:2px 0">' + makeStepImg(o.step_image) + '</div>' : ''
      parts.push('<div class="rc-item">' + pImg + '<strong>' + esc(name) + '</strong>' + (desc ? "：" + desc : "") + (o.color ? '  ·  <span style="color:#666">' + esc(o.color) + '</span>' : "") + '</div>')
    })
  }
  if (data.color_palette && Array.isArray(data.color_palette)) {
    parts.push('<div class="rc-sec">🎨 配色方案</div>')
    data.color_palette.forEach(function(c) {
      var name = c.name || c.color || (typeof c === "string" ? c : "")
      var desc = c.description || c.detail || (typeof c === "string" ? "" : c.note || "")
      parts.push('<div class="rc-item">🎨 ' + esc(name) + (desc ? " \u2014 " + esc(desc) : "") + '</div>')
    })
  }
  if (data.accessories && Array.isArray(data.accessories)) {
    parts.push('<div class="rc-sec">💍 配饰推荐</div>')
    data.accessories.forEach(function(a) {
      var name = a.name || a.item || ""
      var desc = a.description || a.recommendation || a.note || ""
      parts.push('<div class="rc-item">\u00b7 <strong>' + esc(name) + '</strong>' + (desc ? " \u2014 " + esc(desc) : "") + '</div>')
    })
  }
  if (data.materials && Array.isArray(data.materials)) {
    parts.push('<div class="rc-sec">📦 所需材料</div>')
    data.materials.forEach(function(m) {
      if (typeof m === "string") parts.push('<div class="rc-item">\u00b7 ' + esc(m) + '</div>')
      else {
        var mn = esc(m.name || "")
        var ma = m.amount ? " " + esc(m.amount) : ""
        var mn1 = m.note ? ' <span class="rc-note">' + esc(m.note) + '</span>' : ""
        parts.push('<div class="rc-item">\u00b7 <strong>' + mn + '</strong>' + ma + mn1 + '</div>')
      }
    })
  }
  if (data.recommendations && Array.isArray(data.recommendations)) {
    parts.push('<div class="rc-sec">💡 推荐</div>')
    data.recommendations.forEach(function(r) {
      if (typeof r === "string") parts.push('<div class="rc-item">\u00b7 ' + esc(r) + '</div>')
      else {
        var rn = esc(r.name || r.item || "")
        var rd = r.reason || r.description || r.detail
        parts.push('<div class="rc-item">\u00b7 <strong>' + rn + '</strong>' + (rd ? " \u2014 " + esc(rd) : "") + '</div>')
      }
    })
  }
  if (data.tips) {
    parts.push('<div class="rc-sec">💡 小贴士</div>')
    const ta = Array.isArray(data.tips) ? data.tips : [data.tips]
    ta.forEach(t => parts.push(`<div class="rc-item">· ${esc(t)}</div>`))
  }
  // === fallback for unknown fields ===
  var allKeys = Object.keys(data)
  var knownKeys = ["title","difficulty","time","servings","id","_id","__v","createdAt","updatedAt","problem","question","occasion","ingredients","steps","selection_steps","tools","tips","key_point","safety_tip","answer","suggestions","followUps","style","items","outfits","color_palette","accessories","materials","recommendations","category","tags"]
  for (var ki = 0; ki < allKeys.length; ki++) {
    var k = allKeys[ki]
    if (knownKeys.indexOf(k) >= 0) continue
    var v = data[k]
    if (v === null || v === undefined) continue
    if (typeof v === "string" && v.trim()) {
      parts.push('<div class="rc-sec">📌 ' + k + '</div>')
      parts.push('<div class="rc-item">' + esc(v).replace(/\n/g, "<br>") + '</div>')
    } else if (Array.isArray(v) && v.length) {
      parts.push('<div class="rc-sec">📌 ' + k + '</div>')
      for (var vi = 0; vi < v.length; vi++) {
        var item = v[vi]
        if (typeof item === "string") parts.push('<div class="rc-item">\u00b7 ' + esc(item) + '</div>')
        else if (typeof item === "object" && item) {
          var ikeys = Object.keys(item)
          var texts = []
          for (var ti = 0; ti < ikeys.length; ti++) {
            var iv = item[ikeys[ti]]
            if (iv) texts.push(esc(iv))
          }
          parts.push('<div class="rc-item">\u00b7 ' + texts.join(" \u2014 ") + '</div>')
        }
      }
    }
  }

  let recQ = data.followUps || []
  if (!recQ.length) {
    const kw = (data.title || data.question || data.problem || data.品类 || '').replace(/[、，。]/g, ' ').trim()
    if (kw && kw.length > 1) {
      // Detect scene from data fields
      if (data.steps && data.ingredients) {
        // Cooking scene
        recQ = [`${kw}没有某种食材用什么代替`, `${kw}有什么技巧`, `${kw}可以加什么配菜`]
      } else if (data.outfits || data.color_palette || data.occasion) {
        // Fashion scene
        recQ = [`${kw}适合什么场合穿`, `${kw}怎么搭配更好看`, `${kw}推荐什么颜色`]
      } else if (data.selection_steps || data.category) {
        // Shopping scene
        recQ = [`${kw}怎么保存`, `${kw}什么季节最好`, `${kw}有什么注意事项`]
      } else if (data.tools || data.problem) {
        // Repair scene
        recQ = [`${kw}需要什么工具`, `${kw}有什么注意事项`, `${kw}什么情况要找专业人员`]
      } else if (data.suggestions || data.品类) {
        recQ = [`${kw}有什么技巧`, `${kw}要注意什么`, `${kw}推荐什么`]
      } else {
        recQ = [`${kw}怎么做`, `${kw}需要什么`, `${kw}有什么技巧`]
      }
    }
  }
  if (recQ.length) {
    parts.push(`<div class="rc-followups"><div class="rc-followup-title">💡 你可能还想问</div>${recQ.map(q => `<span class="rc-followup-chip">${esc(q)}</span>`).join(' ')}</div>`)
  }
  return '<div class="rc-card">' + parts.join('') + '</div>'
}

function makeStepImg(kw) {
  if (!kw) kw = "cooking"
  const emoji = stepEmoji(kw)
  const cls = stepGradient(kw)
  return '<div class="rc-step-img"><img class="rc-step-photo" src="/api/images/step-img?q=' + encodeURIComponent(kw) + '" alt="' + esc(kw) + '" loading="lazy" onerror="this.style.display=\'none\';this.nextElementSibling.style.display=\'flex\'"/><div class="rc-step-illustration ' + cls + '" style="display:none"><span>' + emoji + '</span></div></div>'
}

function stepEmoji(keyword) {
  if (!keyword) return '🍳'
  const kw = keyword.toLowerCase()
  if (/cut|chop|dice|slice|mince/.test(kw)) return '🔪'
  if (/wash|rinse|clean|peel/.test(kw)) return '🚿'
  if (/fry|stir.?fry|saute|\\bpan\\b/.test(kw)) return '🍳'
  if (/boil|cook|simmer|stew|braise|blanch/.test(kw)) return '🥘'
  if (/steam/.test(kw)) return '♨️'
  if (/bake|roast|oven/.test(kw)) return '🔥'
  if (/season|marinate|salt|sugar|sauce|soy/.test(kw)) return '🧂'
  if (/mix|stir|whisk|beat|blend/.test(kw)) return '🥄'
  if (/egg/.test(kw)) return '🥚'
  if (/meat|ribs|chicken|pork|beef|fish/.test(kw)) return '🥩'
  if (/vegetable|tomato|onion|garlic|ginger/.test(kw)) return '🥬'
  if (/oil|\\bheat\\b/.test(kw)) return '🔥'
  if (/serve|plate|dish|bowl/.test(kw)) return '🍽️'
  if (/garnish|green.?onion|herb/.test(kw)) return '🌿'
  if (/pour|add|drizzle/.test(kw)) return '🫗'
  if (/repair|wrench|screwdriver|pliers|hammer|drill|valve|pipe|faucet|leak|clog|fix|tighten|loosen/.test(kw)) return '🔧'
  if (/clean(?! )|scrub|wipe|mop|sweep|vacuum|dust|polish|organize|tidy|stain|laundry|fold|iron/.test(kw)) return '🧼'
  if (/shirt|tshirt|pants|jeans|shoes|dress|skirt|jacket|coat|\\bhat\\b|belt|tie|scarf|outfit|wear|fashion|style|sneakers|boots|suit|blazer|top|bottom|hoodie|sweater|knit|chino|cardigan|blouse|vest|leather|cotton|linen|collar|pocket|sleeve|cuff|button|zip/.test(kw)) return '👔'
  if (/medicine|pill|tablet|capsule|thermometer|bandage|fever|cough|cold|symptom|health|exercise|vitamin|firstaid/.test(kw)) return '💊'
  if (/dog|cat|pet|puppy|kitten|feed|brush|bath|walk|leash|collar|bone|vet|groom|treat/.test(kw)) return '🐶'
  if (/fruit|apple|banana|orange|grape|vegetable|tomato|fresh|ripe|choose|pick|select|buy|shop|market/.test(kw)) return '🍎'
  return '🍳'
}

function stepGradient(keyword) {
  if (!keyword) return 'grad-cook'
  const kw = keyword.toLowerCase()
  if (/cut|chop|dice|slice|mince/.test(kw)) return 'grad-cut'
  if (/wash|rinse|clean|peel/.test(kw)) return 'grad-wash'
  if (/fry|stir.?fry|saute|\\bpan\\b/.test(kw)) return 'grad-fry'
  if (/boil|cook|simmer|stew|braise|blanch/.test(kw)) return 'grad-boil'
  if (/steam/.test(kw)) return 'grad-steam'
  if (/bake|roast|oven/.test(kw)) return 'grad-bake'
  if (/season|marinate|salt|sugar|sauce|soy/.test(kw)) return 'grad-season'
  if (/mix|stir|whisk|beat|blend/.test(kw)) return 'grad-mix'
  if (/egg/.test(kw)) return 'grad-egg'
  if (/meat|ribs|chicken|pork|beef|fish/.test(kw)) return 'grad-meat'
  if (/vegetable|tomato|onion|garlic|ginger/.test(kw)) return 'grad-veg'
  if (/oil|\\bheat\\b/.test(kw)) return 'grad-oil'
  if (/serve|plate|dish|bowl/.test(kw)) return 'grad-serve'
  if (/garnish|green.?onion|herb/.test(kw)) return 'grad-garnish'
  if (/pour|add|drizzle/.test(kw)) return 'grad-pour'
  if (/repair|wrench|screwdriver|pliers|hammer|drill|valve|pipe|faucet|leak|clog|fix|tighten|loosen/.test(kw)) return 'grad-repair'
  if (/clean(?! )|scrub|wipe|mop|sweep|vacuum|dust|polish|organize|tidy|stain|laundry|fold|iron/.test(kw)) return 'grad-housework'
  if (/shirt|tshirt|pants|jeans|shoes|dress|skirt|jacket|coat|\\bhat\\b|belt|tie|scarf|outfit|wear|fashion|style|sneakers|boots|suit|blazer|top|bottom|hoodie|sweater|knit|chino|cardigan|blouse|vest|leather|cotton|linen|collar|pocket|sleeve|cuff|button|zip/.test(kw)) return 'grad-fashion'
  if (/medicine|pill|tablet|capsule|thermometer|bandage|fever|cough|cold|symptom|health|exercise|vitamin|firstaid/.test(kw)) return 'grad-health'
  if (/dog|cat|pet|puppy|kitten|feed|brush|bath|walk|leash|collar|bone|vet|groom|treat/.test(kw)) return 'grad-pet'
  if (/fruit|apple|banana|orange|grape|vegetable|tomato|fresh|ripe|choose|pick|select|buy|shop|market/.test(kw)) return 'grad-shop'
  return 'grad-cook'
}
function esc(s) { if (typeof s !== 'string') return ''; return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;') }
</script>

<style scoped>
.chat-page { height: 100dvh; min-height: 0; max-height: 100dvh; display: flex; flex-direction: column; overflow: hidden; }
.chat-header { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid #f0f0f0; background: #fff; position: relative; z-index: 30; flex-shrink: 0; box-shadow: 0 1px 0 rgba(15,23,42,.03); }
.header-title { font-size: 16px; font-weight: 600; color: #1a1a1a; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 60%; text-align: center; }
.back-btn { font-size: 14px; }
.messages { flex: 1 1 auto; min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding: 20px 16px 14px; height: auto; }
.welcome { text-align: center; padding: 60px 20px; }
.welcome-icon { font-size: 56px; margin-bottom: 16px; }
.welcome-text { font-size: 15px; color: #999; }
.welcome-prompts { margin-top: 18px; text-align: left; }
.wp-title { font-size: 13px; color: #888; margin-bottom: 10px; text-align: center; }
.wp-grid { display: flex; flex-direction: column; gap: 8px; }
.wp-chip { width: 100%; border: 1px solid #e5f4e9; background: #fbfefc; color: #334155; border-radius: 14px; padding: 10px 12px; font-size: 13px; text-align: left; line-height: 1.4; cursor: pointer; }
.wp-chip:active { transform: scale(.985); background: #ecfdf5; }
.msg { margin-bottom: 20px; }
.msg-user { text-align: right; display: flex; flex-direction: column; align-items: flex-end; }
.msg-user .bubble { display: inline-block; background: linear-gradient(135deg, #22c55e, #16a34a); color: #fff; padding: 12px 18px; border-radius: 18px 18px 4px 18px; font-size: 14px; max-width: 85%; text-align: left; line-height: 1.6; word-break: break-word; box-shadow: 0 2px 8px rgba(34,197,94,.15); }
.msg-assistant .bubble { display: inline-block; background: #fff; border: 1px solid #eee; color: #333; padding: 14px 18px; border-radius: 18px 18px 18px 4px; font-size: 14px; max-width: 98%; text-align: left; line-height: 1.7; word-break: break-word; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.source-badge { display: inline-flex; align-items: center; gap: 4px; margin: 6px 0 0 4px; padding: 4px 9px; border-radius: 999px; font-size: 11px; font-weight: 600; }
.source-badge.free { background: #ecfdf5; color: #15803d; border: 1px solid #bbf7d0; }
.msg-actions { display: flex; gap: 4px; margin-top: 6px; padding-left: 4px; opacity: .6; }
.msg-actions:hover { opacity: 1; }
.thinking { color: #999 !important; font-size: 24px !important; letter-spacing: 4px; }
.dot { animation: blink 1.4s infinite both; }
.dot:nth-child(2) { animation-delay: .2s; }
.dot:nth-child(3) { animation-delay: .4s; }
@keyframes blink { 0%,80%,100% { opacity: 0; } 40% { opacity: 1; } }
.cursor { animation: cursorBlink 0.8s infinite; color: #22c55e; font-weight: bold; }
@keyframes cursorBlink { 0%,50% { opacity: 1; } 51%,100% { opacity: 0; } }
.input-area { display: grid; grid-template-columns: auto auto 1fr auto; align-items: center; gap: 8px; padding: 8px 16px 12px; border-top: 1px solid #e0e0e0; background: #fff; position: relative; z-index: 35; flex-shrink: 0; box-shadow: 0 -8px 22px rgba(15,23,42,.06); }
.input-area :deep(.el-button.is-circle) { width: 40px; min-width: 40px; height: 40px; padding: 0; flex-shrink: 0; }
.input-area :deep(.el-button--small.is-circle) { width: 36px; min-width: 36px; height: 36px; }
.prefill-hint { grid-column: 1 / -1; padding: 7px 10px; border-radius: 10px; background: #f0fdf4; color: #15803d; border: 1px solid #bbf7d0; font-size: 11px; font-weight: 700; text-align: center; }
.input-area :deep(.send-ready) { box-shadow: 0 0 0 3px rgba(34,197,94,.16); transform: translateY(-1px); }
.cost-hint { grid-column: 1 / -1; display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 7px 10px; border-radius: 10px; font-size: 11px; line-height: 1.3; border: 1px solid #e5e7eb; background: #f9fafb; color: #4b5563; }
.cost-hint-main { font-weight: 700; white-space: nowrap; }
.cost-hint-sub { color: #6b7280; text-align: right; }
.cost-hint.normal { background: #eff6ff; border-color: #bfdbfe; color: #1d4ed8; }
.cost-hint.normal .cost-hint-sub { color: #2563eb; }
.cost-hint.free { background: #ecfdf5; border-color: #bbf7d0; color: #15803d; }
.cost-hint.free .cost-hint-sub { color: #16a34a; }
.cost-hint.warn { background: #fff7ed; border-color: #fed7aa; color: #c2410c; }
.cost-hint.warn .cost-hint-sub { color: #ea580c; }
.cost-hint.danger { background: #fef2f2; border-color: #fecaca; color: #b91c1c; }
.cost-hint.danger .cost-hint-sub { color: #dc2626; }
.ask-mode-row { grid-column: 1 / -1; display: flex; align-items: center; gap: 8px; padding: 1px 0 2px; }
.ask-mode-chip { border: 1px solid #e5e7eb; background: #fff; color: #64748b; border-radius: 999px; padding: 5px 10px; font-size: 12px; font-weight: 800; line-height: 1; cursor: pointer; transition: all .16s ease; }
.ask-mode-chip:hover { border-color: #86efac; color: #15803d; }
.ask-mode-chip.active { border-color: #22c55e; background: #ecfdf5; color: #15803d; box-shadow: 0 2px 8px rgba(34,197,94,.12); }
.ask-mode-chip.follow.active { border-color: #60a5fa; background: #eff6ff; color: #1d4ed8; box-shadow: 0 2px 8px rgba(96,165,250,.13); }
.ask-mode-chip:disabled { opacity: .55; cursor: not-allowed; }
.ask-mode-tip { margin-left: auto; color: #94a3b8; font-size: 11px; font-weight: 700; }
.upload-btn { flex-shrink: 0; }
.image-preview-bar { display: flex; align-items: center; gap: 12px; padding: 10px 16px; background: #fff; border-top: 1px solid #e5e7eb; border-bottom: 1px solid #e5e7eb; flex-shrink: 0; }
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

/* 写作模式 */
.writing-modes { margin-top: 20px; text-align: left; padding: 0 20px; }
.wm-title { font-size: 13px; color: #888; margin-bottom: 10px; }
.wm-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.wm-chip { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 12px 6px; border-radius: 12px; cursor: pointer; background: #f9fafb; border: 1px solid #eee; transition: .15s; }
.wm-chip:hover { background: #f0fdf4; border-color: #bbf7d0; transform: translateY(-1px); }
.wm-chip:active { transform: scale(.96); }
.wm-icon { font-size: 24px; }
.wm-label { font-size: 12px; color: #555; font-weight: 500; }

/* 写作内容 */
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
/* 上传进度条 */
.upload-progress-bar { flex-shrink: 0;
  height: 4px;
  background: #e5e7eb;
  border-radius: 2px;
  position: relative;
  margin: 0 16px;
  overflow: hidden;
}
.upload-progress-bar .upb-fill { height: 100%; background: linear-gradient(90deg, #22c55e, #16a34a); border-radius: 2px; transition: width .3s ease; }
.upload-progress-bar .upb-text { position: absolute; right: 0; top: -18px; font-size: 11px; color: #22c55e; font-weight: 600; }

/* 麦克风接听动画 */
.mic-listening.el-button {
  animation: mic-pulse 1.2s ease-in-out infinite;
  box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.5);
}
@keyframes mic-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.5); }
  50% { box-shadow: 0 0 0 8px rgba(239, 68, 68, 0); }
}
/* 代码块 */
.md-code-block {
  background: #1e1e2e;
  color: #cdd6f4;
  padding: 14px 16px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
  margin: 8px 0;
  font-family: 'JetBrains Mono', 'Cascadia Code', 'Fira Code', monospace;
}
.md-code-block code { background: none; color: inherit; padding: 0; font-size: inherit; }
/* 表格 */
.md-table {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 13px;
  border-radius: 8px;
  overflow: hidden;
}
.md-table td {
  border: 1px solid #e5e7eb;
  padding: 8px 12px;
  text-align: left;
}
.md-table tr:nth-child(even) { background: #f9fafb; }
/* 链接 */
.md-content a { color: #22c55e; text-decoration: underline; text-underline-offset: 2px; }
.md-content a:hover { color: #16a34a; }
.export-menu { position: absolute; top: 38px; right: 0; background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; box-shadow: 0 4px 16px rgba(0,0,0,.12); z-index: 9999; min-width: 130px; overflow: hidden; }
.export-menu-item { padding: 10px 16px; cursor: pointer; font-size: 14px; color: #333; transition: background .15s; }
.export-menu-item:hover { background: #f0fdf4; color: #16a34a; }
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
.rc-step-illustration.grad-repair { background: linear-gradient(135deg, #ede9fe, #ddd6fe); }
.rc-step-illustration.grad-housework { background: linear-gradient(135deg, #f0fdfa, #ccfbf1); }
.rc-step-illustration.grad-fashion { background: linear-gradient(135deg, #fdf2f8, #fce7f3); }
.rc-step-illustration.grad-health { background: linear-gradient(135deg, #f0fdf4, #dcfce7); }
.rc-step-illustration.grad-pet { background: linear-gradient(135deg, #fef3c7, #fde68a); }
.rc-step-illustration.grad-shop { background: linear-gradient(135deg, #fef9c3, #fde68a); }
.rc-step-illustration.grad-repair { background: linear-gradient(135deg, #ede9fe, #ddd6fe); }
.rc-step-illustration.grad-housework { background: linear-gradient(135deg, #f0fdfa, #ccfbf1); }
.rc-step-illustration.grad-fashion { background: linear-gradient(135deg, #fdf2f8, #fce7f3); }
.rc-step-illustration.grad-health { background: linear-gradient(135deg, #f0fdf4, #dcfce7); }
.rc-step-illustration.grad-pet { background: linear-gradient(135deg, #fef3c7, #fde68a); }
.rc-step-illustration.grad-shop { background: linear-gradient(135deg, #fef9c3, #fde68a); }
.rc-step-illustration.grad-cook { background: linear-gradient(135deg, #ecfdf5, #a7f3d0); }
.rc-step .rc-note { font-size: 12px; color: #888; margin-top: 3px; }
.rc-step .rc-warning { font-size: 12px; color: #dc2626; display: block; margin-top: 2px; }
/* 成品菜图片 */
.food-image {
  margin-top: 6px;
  cursor: pointer;
  border-radius: 12px;
  overflow: hidden;
  position: relative;
}
.food-image-badge {
  position: absolute;
  left: 10px;
  bottom: 10px;
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(22, 163, 74, .9);
  color: #fff;
  font-size: 11px;
  line-height: 1;
  box-shadow: 0 4px 12px rgba(22, 163, 74, .22);
  pointer-events: none;
}
.food-image-badge.paid { background: rgba(234, 88, 12, .92); }
.food-image-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: space-between;
  padding: 10px 12px;
  margin-top: 8px;
  border: 1px dashed #fed7aa;
  border-radius: 12px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 13px;
}

.food-image img {
  width: 100%;
  border-radius: 12px;
  display: block;
  transition: transform .2s;
}
.food-image img:hover { transform: scale(1.01); }
/* 成品图加载中 */
.food-image-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px;
  background: #f0fdf4;
  border: 1px dashed #bbf7d0;
  border-radius: 12px;
  margin-top: 8px;
}
.fil-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid #bbf7d0;
  border-top-color: #22c55e;
  border-radius: 50%;
  animation: fil-spin 0.8s linear infinite;
  flex-shrink: 0;
}
@keyframes fil-spin {
  to { transform: rotate(360deg); }
}
.fil-text { font-size: 13px; color: #16a34a; font-weight: 500; }
/* 上传进度条 */
.upload-progress-bar { flex-shrink: 0;
  height: 4px;
  background: #e5e7eb;
  border-radius: 2px;
  position: relative;
  margin: 0 16px;
  overflow: hidden;
}
.upload-progress-bar .upb-fill { height: 100%; background: linear-gradient(90deg, #22c55e, #16a34a); border-radius: 2px; transition: width .3s ease; }
.upload-progress-bar .upb-text { position: absolute; right: 0; top: -18px; font-size: 11px; color: #22c55e; font-weight: 600; }
/* 麦克风接听动画 */
.mic-listening.el-button {
  animation: mic-pulse 1.2s ease-in-out infinite;
  box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.5);
}
@keyframes mic-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.5); }
  50% { box-shadow: 0 0 0 8px rgba(239, 68, 68, 0); }
}
/* 代码块 */
.md-code-block {
  background: #1e1e2e;
  color: #cdd6f4;
  padding: 14px 16px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
  margin: 8px 0;
  font-family: 'JetBrains Mono', 'Cascadia Code', 'Fira Code', monospace;
}
.md-code-block code { background: none; color: inherit; padding: 0; font-size: inherit; }
/* 表格 */
.md-table {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 13px;
  border-radius: 8px;
  overflow: hidden;
}
.md-table td {
  border: 1px solid #e5e7eb;
  padding: 8px 12px;
  text-align: left;
}
.md-table tr:nth-child(even) { background: #f9fafb; }
/* 链接 */
.md-content a { color: #22c55e; text-decoration: underline; text-underline-offset: 2px; }
.md-content a:hover { color: #16a34a; }
</style>

